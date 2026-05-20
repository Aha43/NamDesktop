package namdesktop.ui;

import namdesktop.lens.InboxItemRow;
import namdesktop.lens.InboxLens;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.List;

public final class InboxPanel extends JPanel {

    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final InboxTableModel tableModel;
    private JTable table;

    public InboxPanel(NamWorkspace workspace, NamWorkspaceService service) {
        super(new BorderLayout());
        this.workspace  = workspace;
        this.service    = service;
        this.tableModel = new InboxTableModel();

        table = new JTable(tableModel) {
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
            @Override public void mousePressed(MouseEvent e)  { if (e.isPopupTrigger()) showMenu(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showMenu(e); }
        });

        var toolbar = new JToolBar();
        toolbar.setFloatable(false);
        var addButton = new JButton("Add item");
        addButton.addActionListener(e -> addItem());
        toolbar.add(addButton);

        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void refresh() {
        tableModel.setRows(new InboxLens().items(workspace));
    }

    private void showMenu(MouseEvent e) {
        var row = table.rowAtPoint(e.getPoint());
        if (row < 0) return;
        table.setRowSelectionInterval(row, row);

        var selected = tableModel.getRow(row);
        var processItem  = new JMenuItem("Process…");
        var renameItem   = new JMenuItem("Rename");
        var markDoneItem = new JMenuItem("Mark done");
        var deleteItem   = new JMenuItem("Delete");

        var isDone = selected.status() == NodeStatus.DONE;
        processItem.setEnabled(!isDone);
        markDoneItem.setEnabled(!isDone);

        processItem.addActionListener(ev  -> process(selected));
        renameItem.addActionListener(ev   -> rename(selected));
        markDoneItem.addActionListener(ev -> markDone(selected));
        deleteItem.addActionListener(ev   -> delete(selected));

        var menu = new JPopupMenu();
        menu.add(processItem);
        menu.add(renameItem);
        menu.add(markDoneItem);
        menu.addSeparator();
        menu.add(deleteItem);
        menu.show(table, e.getX(), e.getY());
    }

    private void process(InboxItemRow row) {
        var options = new String[]{"Single action", "Project"};
        var choice = JOptionPane.showOptionDialog(
                parent(),
                "What is \"" + row.title() + "\"?",
                "Process inbox item",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (choice < 0) return;
        try {
            if (choice == 0) service.convertInboxItemToNextAction(row.id());
            else             service.convertInboxItemToProject(row.id());
            refresh();
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private void addItem() {
        var title = JOptionPane.showInputDialog(
                parent(), "Enter item title:", "Add Item", JOptionPane.PLAIN_MESSAGE);
        if (title == null || title.isBlank()) return;
        try {
            service.addInboxItem(title.strip());
            refresh();
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private void rename(InboxItemRow row) {
        var title = (String) JOptionPane.showInputDialog(
                parent(), "Enter new title:", "Rename",
                JOptionPane.PLAIN_MESSAGE, null, null, row.title());
        if (title == null || title.isBlank()) return;
        try {
            service.renameNode(row.id(), title.strip());
            refresh();
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private void markDone(InboxItemRow row) {
        try {
            service.markDone(row.id());
            refresh();
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private void delete(InboxItemRow row) {
        try {
            service.deleteLeaf(row.id());
            refresh();
        } catch (IllegalStateException e) {
            showError("This item has sub-items and cannot be deleted directly.");
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private Window parent() {
        return SwingUtilities.getWindowAncestor(this);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(parent(), message, "Error", JOptionPane.ERROR_MESSAGE);
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
