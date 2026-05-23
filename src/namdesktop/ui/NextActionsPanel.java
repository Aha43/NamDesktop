package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.lens.NextActionItemRow;
import namdesktop.lens.NextActionsLens;
import namdesktop.model.NamNode;
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
import java.util.UUID;
import java.util.stream.Collectors;

public final class NextActionsPanel extends JPanel {

    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final NextActionsTableModel tableModel;
    private final JTable table;
    private final JButton upButton;
    private final JButton downButton;
    private List<UUID> currentOrder = List.of();
    private UUID pendingSelection;

    public NextActionsPanel(NamWorkspace workspace, NamWorkspaceService service) {
        super(new BorderLayout());
        this.workspace  = workspace;
        this.service    = service;
        this.tableModel = new NextActionsTableModel();

        upButton   = UiHelper.iconButton("Move up",
                new FlatSVGIcon(NextActionsPanel.class.getResource("/icons/arrow-up.svg")).derive(16, 16));
        downButton = UiHelper.iconButton("Move down",
                new FlatSVGIcon(NextActionsPanel.class.getResource("/icons/arrow-down.svg")).derive(16, 16));
        upButton.setToolTipText("Move selected action up");
        downButton.setToolTipText("Move selected action down");
        upButton.setEnabled(false);
        downButton.setEnabled(false);

        var addButton = UiHelper.iconButton("Add action",
                new FlatSVGIcon(NextActionsPanel.class.getResource("/icons/plus.svg")).derive(16, 16));
        addButton.addActionListener(e -> addAction());

        var toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(addButton);
        toolbar.add(upButton);
        toolbar.add(downButton);
        add(toolbar, BorderLayout.NORTH);

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

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            var row  = table.getSelectedRow();
            var size = tableModel.getRowCount();
            upButton.setEnabled(row > 0);
            downButton.setEnabled(row >= 0 && row < size - 1);
        });

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

        upButton.addActionListener(e -> {
            var row = table.getSelectedRow();
            if (row < 0) return;
            var id = tableModel.getRow(row).id();
            try { pendingSelection = id; service.moveViewItemUp(NamWorkspaceService.VIEW_NEXT_ACTIONS, id, currentOrder); refresh(); }
            catch (java.io.IOException ex) { showError(ex.getMessage()); }
        });
        downButton.addActionListener(e -> {
            var row = table.getSelectedRow();
            if (row < 0) return;
            var id = tableModel.getRow(row).id();
            try { pendingSelection = id; service.moveViewItemDown(NamWorkspaceService.VIEW_NEXT_ACTIONS, id, currentOrder); refresh(); }
            catch (java.io.IOException ex) { showError(ex.getMessage()); }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void refresh() {
        var liveRows   = new NextActionsLens().items(workspace);
        var liveNodes  = liveRows.stream()
                .map(r -> workspace.getNode(r.id()).orElseThrow())
                .toList();
        var ordered    = service.getViewOrder(NamWorkspaceService.VIEW_NEXT_ACTIONS, liveNodes);
        currentOrder   = ordered.stream().map(NamNode::getId).toList();
        var rowById    = liveRows.stream().collect(Collectors.toMap(NextActionItemRow::id, r -> r));
        tableModel.setRows(currentOrder.stream().map(rowById::get).toList());

        if (pendingSelection != null) {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if (tableModel.getRow(i).id().equals(pendingSelection)) {
                    table.setRowSelectionInterval(i, i);
                    pendingSelection = null;
                    break;
                }
            }
        }
    }

    private void addAction() {
        var title = JOptionPane.showInputDialog(this, "Action title:", "Add action", JOptionPane.PLAIN_MESSAGE);
        if (title == null || title.isBlank()) return;
        try {
            service.createNextAction(title.strip());
            refresh();
        } catch (java.io.IOException ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static final class NextActionsTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Title", "Project", "Tags", "Status"};
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
                case 2 -> String.join(", ", r.tags());
                case 3 -> r.status();
                default -> null;
            };
        }
    }
}
