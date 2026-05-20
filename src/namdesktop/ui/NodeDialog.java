package namdesktop.ui;

import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.UUID;

public final class NodeDialog extends JDialog {

    private final UUID nodeId;
    private final NamWorkspaceService service;
    private final JTextArea descriptionArea;
    private final JButton statusButton;
    private NodeStatus currentStatus;

    public NodeDialog(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service) {
        super(parent, ModalityType.APPLICATION_MODAL);
        this.nodeId  = nodeId;
        this.service = service;

        var node = workspace.getNode(nodeId).orElseThrow();
        currentStatus = node.getStatus();

        var titleLabel = new JLabel(node.getTitle());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        statusButton = new JButton(statusLabel());
        statusButton.addActionListener(e -> toggleStatus());

        var toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(statusButton);

        descriptionArea = new JTextArea(node.getDescription() != null ? node.getDescription() : "");
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        var scrollPane = new JScrollPane(descriptionArea);

        var saveButton   = new JButton("Save");
        var cancelButton = new JButton("Cancel");
        saveButton.addActionListener(e -> save());
        cancelButton.addActionListener(e -> dispose());

        var footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(cancelButton);
        footer.add(saveButton);

        var centre = new JPanel(new BorderLayout());
        centre.add(toolbar,    BorderLayout.NORTH);
        centre.add(scrollPane, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(titleLabel, BorderLayout.NORTH);
        add(centre,     BorderLayout.CENTER);
        add(footer,     BorderLayout.SOUTH);

        setTitle(node.getTitle());
        setSize(500, 350);
        setLocationRelativeTo(parent);
    }

    private String statusLabel() {
        return currentStatus == NodeStatus.DONE ? "Mark active" : "Mark done";
    }

    private void toggleStatus() {
        try {
            if (currentStatus == NodeStatus.DONE) {
                service.markActive(nodeId);
                currentStatus = NodeStatus.ACTIVE;
            } else {
                service.markDone(nodeId);
                currentStatus = NodeStatus.DONE;
            }
            statusButton.setText(statusLabel());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void save() {
        try {
            service.updateDescription(nodeId, descriptionArea.getText());
            dispose();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
