package namdesktop.ui;

import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class TagManagementDialog extends JDialog {

    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final TagTableModel tableModel;
    private final JTable table;

    public TagManagementDialog(Window parent, NamWorkspace workspace, NamWorkspaceService service) {
        super(parent, "Manage Tags", ModalityType.APPLICATION_MODAL);
        this.workspace  = workspace;
        this.service    = service;
        this.tableModel = new TagTableModel();

        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(1).setMaxWidth(80);

        var newButton    = new JButton("New tag…");
        var renameButton = new JButton("Rename…");
        var deleteButton = new JButton("Delete");
        newButton.addActionListener(e -> newTag());
        renameButton.addActionListener(e -> renameSelected());
        deleteButton.addActionListener(e -> deleteSelected());

        var toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(newButton);
        toolbar.addSeparator();
        toolbar.add(renameButton);
        toolbar.addSeparator();
        toolbar.add(deleteButton);

        var closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        var footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(closeButton);

        setLayout(new BorderLayout());
        add(toolbar,                  BorderLayout.NORTH);
        add(new JScrollPane(table),   BorderLayout.CENTER);
        add(footer,                   BorderLayout.SOUTH);

        setSize(400, 350);
        setLocationRelativeTo(parent);
        refresh();
    }

    private void newTag() {
        var tag = JOptionPane.showInputDialog(this, "New tag name:", "New tag", JOptionPane.PLAIN_MESSAGE);
        if (tag == null || tag.isBlank()) return;
        try {
            service.registerTag(tag.strip());
            refresh();
        } catch (IOException e) {
            error("Failed to save: " + e.getMessage());
        }
    }

    private void renameSelected() {
        var row = table.getSelectedRow();
        if (row < 0) { warn("Select a tag to rename."); return; }
        var oldTag = tableModel.getTag(row);
        var newTag = JOptionPane.showInputDialog(this, "New name for \"" + oldTag + "\":", oldTag);
        if (newTag == null || newTag.isBlank()) return;
        newTag = newTag.strip().toLowerCase();
        if (newTag.equals(oldTag)) return;
        try {
            service.renameTag(oldTag, newTag);
            refresh();
        } catch (IOException e) {
            error("Failed to rename: " + e.getMessage());
        }
    }

    private void deleteSelected() {
        var row = table.getSelectedRow();
        if (row < 0) { warn("Select a tag to delete."); return; }
        var tag = tableModel.getTag(row);
        var count = tableModel.getCount(row);
        var message = count == 0
                ? "Remove \"" + tag + "\" from the tag list?"
                : "Delete \"" + tag + "\" from " + count + " node(s)? This cannot be undone.";
        var confirm = JOptionPane.showConfirmDialog(this, message,
                "Delete tag", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            service.deleteTag(tag);
            refresh();
        } catch (IOException e) {
            error("Failed to delete: " + e.getMessage());
        }
    }

    private void refresh() {
        var tags = workspace.allTags();
        var rows = new ArrayList<String[]>();
        for (var tag : tags) {
            var count = workspace.getNodes().values().stream()
                    .filter(n -> n.getTags().contains(tag))
                    .count();
            rows.add(new String[]{tag, String.valueOf(count)});
        }
        tableModel.setRows(rows);
    }

    private void warn(String msg)  { JOptionPane.showMessageDialog(this, msg, "No selection", JOptionPane.WARNING_MESSAGE); }
    private void error(String msg) { JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE); }

    private static final class TagTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Tag", "Used by"};
        private List<String[]> rows = List.of();

        void setRows(List<String[]> rows) { this.rows = rows; fireTableDataChanged(); }

        String getTag(int row)   { return rows.get(row)[0]; }
        int    getCount(int row) { return Integer.parseInt(rows.get(row)[1]); }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }
        @Override public Object getValueAt(int row, int col) { return rows.get(row)[col]; }
    }
}
