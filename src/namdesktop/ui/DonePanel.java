package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.lens.DoneItemRow;
import namdesktop.lens.DoneLens;
import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class DonePanel extends JPanel {

    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final Consumer<UUID> onOpenProject;
    private final DoneTableModel tableModel;
    private final JTable table;
    private final JButton deleteButton;
    private final JButton markNextButton;
    private final JButton markBacklogButton;

    public DonePanel(NamWorkspace workspace, NamWorkspaceService service, Consumer<UUID> onOpenProject) {
        super(new BorderLayout());
        this.workspace     = workspace;
        this.service       = service;
        this.onOpenProject = onOpenProject;
        this.tableModel    = new DoneTableModel();

        deleteButton      = UiHelper.iconButton("Delete",
                new FlatSVGIcon(DonePanel.class.getResource("/icons/trash.svg")).derive(16, 16));
        markNextButton    = UiHelper.iconButton("Mark as Next",
                new FlatSVGIcon(DonePanel.class.getResource("/icons/arrow-right.svg")).derive(16, 16));
        markBacklogButton = UiHelper.iconButton("Mark as Backlog",
                new FlatSVGIcon(DonePanel.class.getResource("/icons/archive.svg")).derive(16, 16));

        deleteButton.setToolTipText("Permanently delete selected action");
        markNextButton.setToolTipText("Move back to Next Actions");
        markBacklogButton.setToolTipText("Move back to Backlog");

        deleteButton.setEnabled(false);
        markNextButton.setEnabled(false);
        markBacklogButton.setEnabled(false);

        var toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(deleteButton);
        toolbar.add(markNextButton);
        toolbar.add(markBacklogButton);
        add(toolbar, BorderLayout.NORTH);

        table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                var c = super.prepareRenderer(renderer, row, column);
                c.setForeground(Color.GRAY);
                if (column == 1 && tableModel.getRow(row).parentId() != null) {
                    c.setFont(c.getFont().deriveFont(Font.ITALIC));
                } else {
                    c.setFont(c.getFont().deriveFont(Font.PLAIN));
                }
                return c;
            }

            @Override
            public String getToolTipText(MouseEvent e) {
                var row = rowAtPoint(e.getPoint());
                var col = columnAtPoint(e.getPoint());
                if (row < 0 || col != 1) return null;
                return tableModel.getRow(row).projectPath();
            }
        };
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getColumn("Tags").setCellRenderer(UiHelper.tagsRenderer());

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            var selected = table.getSelectedRow() >= 0;
            deleteButton.setEnabled(selected);
            markNextButton.setEnabled(selected);
            markBacklogButton.setEnabled(selected);
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                var row = table.rowAtPoint(e.getPoint());
                var col = table.columnAtPoint(e.getPoint());
                if (row < 0) return;
                var item = tableModel.getRow(row);
                if (col == 1 && e.getClickCount() == 1 && item.parentId() != null) {
                    onOpenProject.accept(item.parentId());
                    return;
                }
                if (e.getClickCount() == 2 && col != 1) {
                    new ActionDialog(SwingUtilities.getWindowAncestor(DonePanel.this),
                            item.id(), workspace, service, false, DonePanel.this::refresh).setVisible(true);
                }
            }
        });

        deleteButton.addActionListener(e -> deleteSelected());
        markNextButton.addActionListener(e -> withSelected(id -> {
            var title = workspace.getNode(id).map(n -> n.getTitle()).orElse("this action");
            var confirm = JOptionPane.showConfirmDialog(this,
                    "Move \"" + title + "\" back to Next Actions?",
                    "Confirm", JOptionPane.OK_CANCEL_OPTION);
            if (confirm != JOptionPane.OK_OPTION) return;
            try { service.markNext(id); refreshKeepSelection(); }
            catch (java.io.IOException ex) { showError(ex.getMessage()); }
        }));
        markBacklogButton.addActionListener(e -> withSelected(id -> {
            var title = workspace.getNode(id).map(n -> n.getTitle()).orElse("this action");
            var confirm = JOptionPane.showConfirmDialog(this,
                    "Move \"" + title + "\" back to Backlog?",
                    "Confirm", JOptionPane.OK_CANCEL_OPTION);
            if (confirm != JOptionPane.OK_OPTION) return;
            try { service.markBacklog(id); refreshKeepSelection(); }
            catch (java.io.IOException ex) { showError(ex.getMessage()); }
        }));

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void refresh() {
        tableModel.setRows(new DoneLens().items(workspace));
        if (tableModel.getRowCount() > 0 && table.getSelectedRow() < 0)
            table.setRowSelectionInterval(0, 0);
        var row = table.getSelectedRow();
        deleteButton.setEnabled(row >= 0);
        markNextButton.setEnabled(row >= 0);
        markBacklogButton.setEnabled(row >= 0);
    }

    private void refreshKeepSelection() {
        var row = table.getSelectedRow();
        refresh();
        if (tableModel.getRowCount() > 0 && row >= 0) {
            var newRow = Math.min(row, tableModel.getRowCount() - 1);
            table.setRowSelectionInterval(newRow, newRow);
        }
    }

    private void deleteSelected() {
        withSelected(id -> {
            var title = workspace.getNode(id).map(n -> n.getTitle()).orElse("this action");
            var confirm = JOptionPane.showConfirmDialog(this,
                    "Permanently delete \"" + title + "\"?",
                    "Delete", JOptionPane.OK_CANCEL_OPTION);
            if (confirm != JOptionPane.OK_OPTION) return;
            try {
                service.deleteLeaf(id);
                refreshKeepSelection();
            } catch (java.io.IOException ex) {
                showError(ex.getMessage());
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        });
    }

    private void withSelected(java.util.function.Consumer<UUID> action) {
        var row = table.getSelectedRow();
        if (row < 0) return;
        try {
            action.accept(tableModel.getRow(row).id());
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static final class DoneTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Action", "Project", "Tags"};
        private List<DoneItemRow> rows = List.of();

        void setRows(List<DoneItemRow> rows) { this.rows = rows; fireTableDataChanged(); }
        DoneItemRow getRow(int i) { return rows.get(i); }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            var r = rows.get(row);
            return switch (col) {
                case 0 -> r.title();
                case 1 -> r.parentTitle() != null ? r.parentTitle() : "";
                case 2 -> new String[]{
                        String.join(", ", r.tags()),
                        String.join(", ", r.inheritedTags())};
                default -> null;
            };
        }

        @Override
        public Class<?> getColumnClass(int col) {
            return col == 2 ? String[].class : String.class;
        }
    }
}
