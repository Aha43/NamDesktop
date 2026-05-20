package namdesktop.ui;

import namdesktop.lens.BacklogItemRow;
import namdesktop.lens.BacklogLens;
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

public final class BacklogPanel extends JPanel {

    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final BacklogTableModel tableModel;

    public BacklogPanel(NamWorkspace workspace, NamWorkspaceService service) {
        super(new BorderLayout());
        this.workspace  = workspace;
        this.service    = service;
        this.tableModel = new BacklogTableModel();

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
                new ActionDialog(SwingUtilities.getWindowAncestor(BacklogPanel.this),
                        selected.id(), workspace, service, false, BacklogPanel.this::refresh).setVisible(true);
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void refresh() {
        tableModel.setRows(new BacklogLens().items(workspace));
    }

    private static final class BacklogTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Title", "Status"};
        private List<BacklogItemRow> rows = List.of();

        void setRows(List<BacklogItemRow> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        BacklogItemRow getRow(int index) { return rows.get(index); }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            var r = rows.get(row);
            return switch (col) {
                case 0 -> r.title();
                case 1 -> r.status();
                default -> null;
            };
        }
    }
}
