package namdesktop.ui;

import namdesktop.model.NodeStatus;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Contextual bar for bulk actions on the checked rows (#402). Appears when ≥1 row is checked and
 * offers Set status / Add tag / Delete, operating on the ids from {@code selectedIds} via the
 * batch service primitives. After any action it calls {@code afterChange} (the panel clears the
 * selection and refreshes). Reused across the list panels and the Project Workbench.
 */
final class BulkActionBar extends JPanel {

    private final NamWorkspaceService  service;
    private final Supplier<List<UUID>> selectedIds;
    private final Runnable             afterChange;
    private final JLabel               countLabel = new JLabel();

    BulkActionBar(NamWorkspaceService service, Supplier<List<UUID>> selectedIds, Runnable afterChange) {
        super(new FlowLayout(FlowLayout.LEFT, 8, 4));
        this.service     = service;
        this.selectedIds = selectedIds;
        this.afterChange = afterChange;
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")));

        var setStatus = new JButton("Set status ▾");
        setStatus.addActionListener(e -> {
            var menu = new JPopupMenu();
            statusItem(menu, "Next", NodeStatus.NEXT);
            statusItem(menu, "Backlog", NodeStatus.BACKLOG);
            statusItem(menu, "Done", NodeStatus.DONE);
            menu.show(setStatus, 0, setStatus.getHeight());
        });
        var addTag = new JButton("Add tag…");
        addTag.addActionListener(e -> addTag());
        var delete = new JButton("Delete");
        delete.addActionListener(e -> delete());

        add(countLabel);
        add(setStatus);
        add(addTag);
        add(delete);
        setVisible(false);
    }

    /** Updates the count label and shows/hides the bar. */
    void setCount(int n) {
        countLabel.setText(n + " selected");
        setVisible(n > 0);
        revalidate();
        repaint();
    }

    private void statusItem(JPopupMenu menu, String label, NodeStatus status) {
        var item = new JMenuItem(label);
        item.addActionListener(e -> applyStatus(status));
        menu.add(item);
    }

    private void applyStatus(NodeStatus status) {
        var ids = selectedIds.get();
        if (ids.isEmpty() || !MonitoringModeGuard.checkAndConfirm(this)) return;
        try {
            service.setStatusForAll(ids, status);
            afterChange.run();
        } catch (java.io.IOException ex) { error(ex); }
    }

    private void addTag() {
        var ids = selectedIds.get();
        if (ids.isEmpty() || !MonitoringModeGuard.checkAndConfirm(this)) return;
        var tag = JOptionPane.showInputDialog(this, "Tag to add to " + ids.size() + " item(s):",
                "Add tag", JOptionPane.PLAIN_MESSAGE);
        if (tag == null || tag.isBlank()) return;
        try {
            service.addTagToAll(ids, tag);
            afterChange.run();
        } catch (java.io.IOException ex) { error(ex); }
    }

    private void delete() {
        var ids = selectedIds.get();
        if (ids.isEmpty() || !MonitoringModeGuard.checkAndConfirm(this)) return;
        var ok = JOptionPane.showConfirmDialog(this, "Delete " + ids.size() + " items?", "Delete",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        try {
            var skipped = service.deleteLeaves(ids);
            afterChange.run();
            if (!skipped.isEmpty())
                JOptionPane.showMessageDialog(this, skipped.size() + " item(s) had sub-items and were not deleted.",
                        "Delete", JOptionPane.WARNING_MESSAGE);
        } catch (java.io.IOException ex) { error(ex); }
    }

    private void error(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}
