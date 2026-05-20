package namdesktop.ui;

import namdesktop.lens.SearchLens;
import namdesktop.lens.SearchResultRow;
import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public final class SearchPanel extends JPanel {

    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final SearchTableModel tableModel;
    private final JTextField searchField;

    public SearchPanel(NamWorkspace workspace, NamWorkspaceService service) {
        super(new BorderLayout());
        this.workspace  = workspace;
        this.service    = service;
        this.tableModel = new SearchTableModel();

        searchField = new JTextField();
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { runSearch(); }
            public void removeUpdate(DocumentEvent e)  { runSearch(); }
            public void changedUpdate(DocumentEvent e) { runSearch(); }
        });

        var north = new JPanel(new BorderLayout(6, 0));
        north.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        north.add(new JLabel("Search:"), BorderLayout.WEST);
        north.add(searchField,           BorderLayout.CENTER);

        JTable table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                var row = table.rowAtPoint(e.getPoint());
                if (row < 0) return;
                openDialog(tableModel.getRow(row));
            }
        });

        add(north,                  BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void refresh() {
        SwingUtilities.invokeLater(searchField::requestFocusInWindow);
        runSearch();
    }

    private void runSearch() {
        tableModel.setRows(new SearchLens().search(workspace, searchField.getText()));
    }

    private void openDialog(SearchResultRow row) {
        var owner = SwingUtilities.getWindowAncestor(this);
        if ("Project".equals(row.type())) {
            new ProjectDialog(owner, row.id(), workspace, service, this::runSearch).setVisible(true);
        } else {
            new ActionDialog(owner, row.id(), workspace, service, false, this::runSearch).setVisible(true);
        }
    }

    private static final class SearchTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Title", "Type", "Project"};
        private List<SearchResultRow> rows = List.of();

        void setRows(List<SearchResultRow> rows) { this.rows = rows; fireTableDataChanged(); }
        SearchResultRow getRow(int i) { return rows.get(i); }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            var r = rows.get(row);
            return switch (col) {
                case 0 -> r.title();
                case 1 -> r.type();
                case 2 -> r.parentTitle() != null ? r.parentTitle() : "";
                default -> null;
            };
        }
    }
}
