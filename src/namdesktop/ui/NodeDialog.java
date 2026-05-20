package namdesktop.ui;

import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NodeDialog extends JDialog {

    private final UUID nodeId;
    private final NamWorkspaceService service;
    private final JTextArea descriptionArea;
    private final JTextField tagsField;
    private final JButton statusButton;
    private final JToolBar toolbar;
    private final JPanel centre;
    private final Runnable onChanged;
    private NodeStatus currentStatus;

    public NodeDialog(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service) {
        this(parent, nodeId, workspace, service, () -> {});
    }

    public NodeDialog(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service, Runnable onChanged) {
        super(parent, ModalityType.APPLICATION_MODAL);
        this.nodeId    = nodeId;
        this.service   = service;
        this.onChanged = onChanged;

        var node = workspace.getNode(nodeId).orElseThrow();
        currentStatus = node.getStatus();

        var titleLabel = new JLabel(node.getTitle());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        statusButton = new JButton(statusLabel());
        statusButton.addActionListener(e -> toggleStatus());

        var deleteButton = new JButton("Delete");
        deleteButton.addActionListener(e -> delete(node.getTitle()));

        toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(statusButton);
        toolbar.addSeparator();
        toolbar.add(deleteButton);

        descriptionArea = new JTextArea(node.getDescription() != null ? node.getDescription() : "");
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        var scrollPane = new JScrollPane(descriptionArea);

        tagsField = new JTextField(String.join(", ", node.getTags()));
        tagsField.setToolTipText("Comma-separated tags, e.g. @computer, @home");
        var tagsLabel = new JLabel("Tags:");
        tagsLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        var tagsRow = new JPanel(new BorderLayout());
        tagsRow.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        tagsRow.add(tagsLabel,  BorderLayout.WEST);
        tagsRow.add(tagsField,  BorderLayout.CENTER);

        var saveButton   = new JButton("Save");
        var cancelButton = new JButton("Cancel");
        saveButton.addActionListener(e -> save());
        cancelButton.addActionListener(e -> dispose());

        var footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(cancelButton);
        footer.add(saveButton);

        centre = new JPanel(new BorderLayout());
        centre.add(toolbar,    BorderLayout.NORTH);
        centre.add(scrollPane, BorderLayout.CENTER);
        centre.add(tagsRow,    BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(titleLabel, BorderLayout.NORTH);
        add(centre,     BorderLayout.CENTER);
        add(footer,     BorderLayout.SOUTH);

        setTitle(node.getTitle());
        setSize(500, 350);
        setLocationRelativeTo(parent);
    }

    private String statusLabel() {
        return currentStatus == NodeStatus.DONE ? "Mark next" :
               currentStatus == NodeStatus.NEXT ? "Mark done" : "Mark next";
    }

    private void toggleStatus() {
        try {
            if (currentStatus == NodeStatus.NEXT) {
                service.markDone(nodeId);
                currentStatus = NodeStatus.DONE;
            } else {
                service.markNext(nodeId);
                currentStatus = NodeStatus.NEXT;
            }
            statusButton.setText(statusLabel());
            notifyChanged();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void delete(String title) {
        var choice = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete \"" + title + "\"? This cannot be undone.",
                "Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        try {
            service.deleteLeaf(nodeId);
            notifyChanged();
            dispose();
        } catch (IllegalStateException e) {
            JOptionPane.showMessageDialog(this,
                    "This item has sub-items and cannot be deleted directly.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to delete: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void save() {
        try {
            service.updateDescription(nodeId, descriptionArea.getText());
            service.updateTags(nodeId, parseTags(tagsField.getText()));
            notifyChanged();
            dispose();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static List<String> parseTags(String text) {
        var result = new ArrayList<String>();
        for (var part : text.split(",")) {
            var tag = part.strip().toLowerCase();
            if (!tag.isEmpty()) result.add(tag);
        }
        return result;
    }

    protected void notifyChanged() { onChanged.run(); }

    protected void addToolbarButton(JButton button) {
        toolbar.addSeparator();
        toolbar.add(button);
    }

    protected void addBelowDescription(JComponent component) {
        centre.add(component, BorderLayout.SOUTH);
    }
}
