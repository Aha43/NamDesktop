package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.app.AppSettings;
import namdesktop.lens.BacklogItemRow;
import namdesktop.lens.BacklogLens;
import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class BacklogPanel extends JPanel {

    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final Consumer<UUID> onOpenProject;
    private final BacklogTableModel tableModel;
    private final JTable table;
    private final JButton upButton;
    private final JButton downButton;
    private List<UUID> currentOrder = List.of();
    private UUID pendingSelection;
    private TableColumn statusColumn;
    private boolean statusColumnVisible = true;

    public BacklogPanel(NamWorkspace workspace, NamWorkspaceService service, Consumer<UUID> onOpenProject) {
        super(new BorderLayout());
        this.workspace     = workspace;
        this.service       = service;
        this.onOpenProject = onOpenProject;
        this.tableModel    = new BacklogTableModel();

        upButton   = UiHelper.iconButton("Move up",
                new FlatSVGIcon(BacklogPanel.class.getResource("/icons/arrow-up.svg")).derive(16, 16));
        downButton = UiHelper.iconButton("Move down",
                new FlatSVGIcon(BacklogPanel.class.getResource("/icons/arrow-down.svg")).derive(16, 16));
        upButton.setToolTipText("Move selected action up");
        downButton.setToolTipText("Move selected action down");
        upButton.setEnabled(false);
        downButton.setEnabled(false);

        var addButton = UiHelper.iconButton("Add action",
                new FlatSVGIcon(BacklogPanel.class.getResource("/icons/plus.svg")).derive(16, 16));
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
                var item = tableModel.getRow(row);
                c.setForeground(item.status() == NodeStatus.DONE ? Color.GRAY : getForeground());
                int style = (item.isInboxItem() || (column == 1 && item.isSubProject()))
                        ? Font.ITALIC : Font.PLAIN;
                c.setFont(c.getFont().deriveFont(style));
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
                var row = table.rowAtPoint(e.getPoint());
                var col = table.columnAtPoint(e.getPoint());
                if (row < 0) return;
                var item = tableModel.getRow(row);
                if (col == 1 && e.getClickCount() == 1 && item.parentId() != null) {
                    onOpenProject.accept(item.parentId());
                    return;
                }
                if (e.getClickCount() == 2 && col != 1) {
                    new ActionDialog(SwingUtilities.getWindowAncestor(BacklogPanel.this),
                            item.id(), workspace, service, false, BacklogPanel.this::refresh).setVisible(true);
                }
            }
        });

        upButton.addActionListener(e -> {
            var row = table.getSelectedRow();
            if (row < 0) return;
            var id = tableModel.getRow(row).id();
            try { pendingSelection = id; service.moveViewItemUp(NamWorkspaceService.VIEW_BACKLOG, id, currentOrder); refresh(); }
            catch (java.io.IOException ex) { showError(ex.getMessage()); }
        });
        downButton.addActionListener(e -> {
            var row = table.getSelectedRow();
            if (row < 0) return;
            var id = tableModel.getRow(row).id();
            try { pendingSelection = id; service.moveViewItemDown(NamWorkspaceService.VIEW_BACKLOG, id, currentOrder); refresh(); }
            catch (java.io.IOException ex) { showError(ex.getMessage()); }
        });

        statusColumn = table.getColumnModel().getColumn(3);
        applyColumnVisibility(AppSettings.getInstance().isShowStatusColumn());

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void applyColumnVisibility(boolean show) {
        var cm = table.getColumnModel();
        if (show == statusColumnVisible) return;
        if (show) {
            cm.addColumn(statusColumn);
            cm.moveColumn(cm.getColumnCount() - 1, 3);
        } else {
            cm.removeColumn(statusColumn);
        }
        statusColumnVisible = show;
    }

    public void refresh() {
        var liveRows  = new BacklogLens().items(workspace);
        var liveNodes = liveRows.stream()
                .map(r -> workspace.getNode(r.id()).orElseThrow())
                .toList();
        var ordered   = service.getViewOrder(NamWorkspaceService.VIEW_BACKLOG, liveNodes);
        currentOrder  = ordered.stream().map(NamNode::getId).toList();
        var rowById   = liveRows.stream().collect(Collectors.toMap(BacklogItemRow::id, r -> r));
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
            service.createBacklogAction(title.strip());
            refresh();
        } catch (java.io.IOException ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static final class BacklogTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Title", "Project", "Tags", "Status"};
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
                case 1 -> r.parentTitle() != null ? r.parentTitle() : "";
                case 2 -> String.join(", ", r.tags());
                case 3 -> r.status();
                default -> null;
            };
        }
    }
}
