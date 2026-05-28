package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.ui.UiHelper;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NodeDialog extends JDialog {

    protected final UUID nodeId;
    protected final NamWorkspaceService service;
    private final String originalTitle;
    private final JTextField titleField;
    private final JTextArea descriptionArea;
    private final TagsField tagsField;
    private final JPanel statusPanel;
    private final JToggleButton backlogBtn = new JToggleButton("Backlog");
    private final JToggleButton nextBtn    = new JToggleButton("Next");
    private final JToggleButton doneBtn    = new JToggleButton("Done");
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
        currentStatus  = node.getStatus();
        originalTitle  = node.getTitle();

        titleField = new JTextField(originalTitle);
        titleField.setFont(titleField.getFont().deriveFont(Font.BOLD, 16f));
        titleField.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        var statusGroup = new ButtonGroup();
        statusGroup.add(backlogBtn);
        statusGroup.add(nextBtn);
        statusGroup.add(doneBtn);
        syncStatusButtons();
        backlogBtn.addActionListener(e -> setStatus(NodeStatus.BACKLOG));
        nextBtn.addActionListener(e -> setStatus(NodeStatus.NEXT));
        doneBtn.addActionListener(e -> setStatus(NodeStatus.DONE));

        statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        statusPanel.setOpaque(false);
        statusPanel.add(backlogBtn);
        statusPanel.add(nextBtn);
        statusPanel.add(doneBtn);

        var deleteButton = UiHelper.iconButton("Delete", new FlatSVGIcon(NodeDialog.class.getResource("/icons/trash.svg")).derive(16, 16));
        deleteButton.addActionListener(e -> delete(originalTitle));

        toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(statusPanel);
        toolbar.addSeparator();
        toolbar.add(deleteButton);

        descriptionArea = new JTextArea(node.getDescription() != null ? node.getDescription() : "");
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setToolTipText("Ctrl+Enter to save");
        descriptionArea.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "save");
        descriptionArea.getActionMap().put("save", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { getRootPane().getDefaultButton().doClick(); }
        });
        var scrollPane = new JScrollPane(descriptionArea);

        tagsField = new TagsField(this, workspace::allTags);
        tagsField.setText(String.join(", ", node.getTags()));
        tagsField.setToolTipText("Comma-separated tags, e.g. @computer, @home");
        var tagsLabel = new JLabel("Tags:");
        tagsLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        var tagsRow = new JPanel(new BorderLayout());
        tagsRow.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        tagsRow.add(tagsLabel, BorderLayout.WEST);
        tagsRow.add(tagsField, BorderLayout.CENTER);

        var saveButton   = new JButton("Save");
        var cancelButton = new JButton("Cancel");
        saveButton.addActionListener(e -> save());
        cancelButton.addActionListener(e -> dispose());

        var footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(cancelButton);
        footer.add(saveButton);
        getRootPane().setDefaultButton(saveButton);

        // Inner panel keeps description + tags together; leaves centre's SOUTH free for subclasses
        var descPanel = new JPanel(new BorderLayout());
        descPanel.add(scrollPane, BorderLayout.CENTER);
        descPanel.add(tagsRow,    BorderLayout.SOUTH);

        centre = new JPanel(new BorderLayout());
        centre.add(toolbar,    BorderLayout.NORTH);
        centre.add(descPanel,  BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(titleField, BorderLayout.NORTH);
        add(centre,     BorderLayout.CENTER);
        add(footer,     BorderLayout.SOUTH);

        setTitle(node.getTitle());
        setSize(500, 350);
        setLocationRelativeTo(parent);
    }

    private void syncStatusButtons() {
        backlogBtn.setSelected(currentStatus == NodeStatus.BACKLOG);
        nextBtn.setSelected(currentStatus == NodeStatus.NEXT);
        doneBtn.setSelected(currentStatus == NodeStatus.DONE);
    }

    private void setStatus(NodeStatus status) {
        if (status == currentStatus) return;
        try {
            switch (status) {
                case BACKLOG -> service.markBacklog(nodeId);
                case NEXT    -> service.markNext(nodeId);
                case DONE    -> { service.markDone(nodeId); onMarkedDone(nodeId, service); }
            }
            currentStatus = status;
            syncStatusButtons();
            notifyChanged();
        } catch (IOException e) {
            syncStatusButtons(); // revert button selection
            JOptionPane.showMessageDialog(this, "Failed to save: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    protected void onMarkedDone(UUID nodeId, NamWorkspaceService service) {}

    private void delete(String title) {
        var choice = JOptionPane.showConfirmDialog(this,
                deleteConfirmMessage(title),
                "Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        doDelete();
    }

    protected String deleteConfirmMessage(String title) {
        return "Are you sure you want to delete \"" + title + "\"? This cannot be undone.";
    }

    protected void doDelete() {
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
        var newTitle = titleField.getText().strip();
        if (newTitle.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Title cannot be blank.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            if (!newTitle.equals(originalTitle)) service.renameNode(nodeId, newTitle);
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

    protected void hideStatusButton() {
        toolbar.remove(statusPanel);
        toolbar.revalidate();
        toolbar.repaint();
    }

    protected void addToolbarButton(JButton button) {
        toolbar.addSeparator();
        toolbar.add(button);
    }

    protected void setDoneButtonEnabled(boolean enabled) { doneBtn.setEnabled(enabled); }

    protected void addBelowDescription(JComponent component) {
        centre.add(component, BorderLayout.SOUTH);
    }
}
