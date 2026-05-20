package namdesktop.ui;

import namdesktop.lens.InboxItemRow;
import namdesktop.lens.InboxLens;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.List;

public final class InboxPanel extends JPanel {

    private final NamWorkspace workspace;
    private final InboxTableModel tableModel;

    public InboxPanel(NamWorkspace workspace) {
        super(new BorderLayout());
        this.workspace  = workspace;
        this.tableModel = new InboxTableModel();

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

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void refresh() {
        tableModel.setRows(new InboxLens().items(workspace));
    }

    private static final class InboxTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Title", "Status"};
        private List<InboxItemRow> rows = List.of();

        void setRows(List<InboxItemRow> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        InboxItemRow getRow(int index) { return rows.get(index); }

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
