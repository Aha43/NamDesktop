package namdesktop.ui;

import namdesktop.lens.NextActionItemRow;
import namdesktop.lens.NextActionsLens;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public final class NextActionsPanel extends JPanel {

    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final NextActionsTableModel tableModel;

    public NextActionsPanel(NamWorkspace workspace, NamWorkspaceService service) {
        super(new BorderLayout());
        this.workspace  = workspace;
        this.service    = service;
        this.tableModel = new NextActionsTableModel();

        JTable table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                var c = super.prepareRenderer(renderer, row, column);
                var status = tableModel.getRow(row).status();
                c.setForeground(status == NodeStatus.DONE ? Color.GRAY : getForeground());
                return c;
            }
        };
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                var row = table.rowAtPoint(e.getPoint());
                if (row < 0) return;
                var selected = tableModel.getRow(row);
                new ActionDialog(SwingUtilities.getWindowAncestor(NextActionsPanel.this),
                        selected.id(), workspace, service, true, NextActionsPanel.this::refresh).setVisible(true);
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void refresh() {
        tableModel.setRows(new NextActionsLens().items(workspace));
    }

    private static final class NextActionsTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Title", "Project", "Status"};
        private List<NextActionItemRow> rows = List.of();

        void setRows(List<NextActionItemRow> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        NextActionItemRow getRow(int index) { return rows.get(index); }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            var r = rows.get(row);
            return switch (col) {
                case 0 -> r.title();
                case 1 -> r.parentTitle() != null ? r.parentTitle() : "";
                case 2 -> r.status();
                default -> null;
            };
        }
    }
}
