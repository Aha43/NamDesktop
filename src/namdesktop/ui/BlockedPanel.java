package namdesktop.ui;

import namdesktop.lens.BlockedLens;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class BlockedPanel extends JPanel {

    private record FlatRow(boolean header, NamNode node, UUID parentId, String projectPath) {}

    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final Consumer<UUID> onOpenProject;
    private final BlockedTableModel tableModel;
    private final JTable table;
    private JPanel tableCard;

    public BlockedPanel(NamWorkspace workspace, NamWorkspaceService service, Consumer<UUID> onOpenProject) {
        super(new BorderLayout());
        this.workspace     = workspace;
        this.service       = service;
        this.onOpenProject = onOpenProject;
        this.tableModel    = new BlockedTableModel();

        var badgeRenderer = UiHelper.actionBadgeRenderer(
                row -> tableModel.getRow(row).header() ? NodeStatus.NEXT
                        : tableModel.getRow(row).node().getStatus());

        table = new JTable(tableModel) {
            @Override
            public String getToolTipText(MouseEvent e) {
                var row = rowAtPoint(e.getPoint());
                var col = columnAtPoint(e.getPoint());
                if (row < 0) return null;
                var flatRow = tableModel.getRow(row);
                if (flatRow.header()) return "Prerequisite — click to open";
                if (col == 0) {
                    var r = getCellRect(row, 0, false);
                    if (e.getX() >= r.x + r.width - UiHelper.ACTION_PENCIL_W)
                        return "Edit: " + flatRow.node().getTitle();
                }
                if (col == 1) return flatRow.projectPath();
                return null;
            }
        };
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);

        table.getColumn("Action").setCellRenderer((t, value, isSelected, hasFocus, row, col) -> {
            var flatRow = tableModel.getRow(row);
            if (flatRow.header()) {
                var bg    = UIManager.getColor("Table.alternateRowColor");
                var label = new JLabel("  " + flatRow.node().getTitle());
                label.setFont(label.getFont().deriveFont(Font.BOLD));
                label.setOpaque(true);
                label.setBackground(bg != null ? bg : t.getBackground());
                label.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
                return label;
            }
            return badgeRenderer.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
        });

        table.getColumn("Project").setCellRenderer((t, value, isSelected, hasFocus, row, col) -> {
            var flatRow = tableModel.getRow(row);
            if (flatRow.header()) {
                var bg    = UIManager.getColor("Table.alternateRowColor");
                var label = new JLabel("");
                label.setOpaque(true);
                label.setBackground(bg != null ? bg : t.getBackground());
                return label;
            }
            var def = new JLabel(value != null ? value.toString() : "");
            def.setOpaque(true);
            def.setBackground(isSelected ? t.getSelectionBackground() : t.getBackground());
            def.setForeground(isSelected ? t.getSelectionForeground() : t.getForeground());
            def.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            return def;
        });

        table.getColumnModel().getColumn(0).setPreferredWidth(250);
        UiHelper.fillTableColumn(table, 1);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                var row = table.rowAtPoint(e.getPoint());
                var col = table.columnAtPoint(e.getPoint());
                if (row < 0) return;
                var flatRow = tableModel.getRow(row);

                if (flatRow.header()) {
                    table.clearSelection();
                    new ActionDialog(SwingUtilities.getWindowAncestor(BlockedPanel.this),
                            flatRow.node().getId(), workspace, service, true, BlockedPanel.this::refresh)
                            .setVisible(true);
                    return;
                }

                var item = flatRow.node();
                if (col == 1) {
                    if (e.getClickCount() == 1 && flatRow.parentId() != null)
                        onOpenProject.accept(flatRow.parentId());
                    return;
                }
                if (col == 0) {
                    var cellRect = table.getCellRect(row, 0, false);
                    if (e.getX() < cellRect.x + UiHelper.ACTION_BADGE_W) {
                        if (e.getClickCount() == 1) showStatusPopup(row, e.getComponent(), e.getX(), e.getY());
                    } else if (e.getX() >= cellRect.x + cellRect.width - UiHelper.ACTION_PENCIL_W) {
                        openDialog(item.getId());
                    } else if (e.getClickCount() == 2) {
                        openDialog(item.getId());
                    }
                }
            }
        });

        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "openDialog");
        table.getActionMap().put("openDialog", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent ev) {
                var row = table.getSelectedRow();
                if (row < 0) return;
                var flatRow = tableModel.getRow(row);
                openDialog(flatRow.node().getId());
            }
        });

        tableCard = UiHelper.tableCard(new JScrollPane(table), "No actions are blocked right now.");
        add(tableCard, BorderLayout.CENTER);
    }

    private void openDialog(UUID nodeId) {
        new ActionDialog(SwingUtilities.getWindowAncestor(this),
                nodeId, workspace, service, true, this::refresh).setVisible(true);
    }

    private void showStatusPopup(int row, Component comp, int x, int y) {
        var id      = tableModel.getRow(row).node().getId();
        var current = tableModel.getRow(row).node().getStatus();
        var popup   = new JPopupMenu();
        for (var entry : new Object[][]{ {"Next", NodeStatus.NEXT}, {"Backlog", NodeStatus.BACKLOG}, {"Done", NodeStatus.DONE} }) {
            var label  = (String) entry[0];
            var target = (NodeStatus) entry[1];
            var letter = switch (target) { case NEXT -> "N"; case BACKLOG -> "B"; default -> "D"; };
            var mi     = new JMenuItem((current == target ? "✓ " : "  ") + letter + "  " + label);
            mi.setEnabled(current != target && target != NodeStatus.DONE);
            mi.addActionListener(e -> {
                if (!MonitoringModeGuard.checkAndConfirm(comp)) return;
                try {
                    switch (target) {
                        case NEXT    -> service.markNext(id);
                        case BACKLOG -> service.markBacklog(id);
                        case DONE    -> service.markDone(id);
                        default      -> {}
                    }
                    if (target == NodeStatus.DONE) {
                        var unblocked = service.newlyUnblockedNames(id);
                        if (!unblocked.isEmpty())
                            MainFrame.showNudge("Unblocked: " + String.join(", ", unblocked));
                    }
                    refresh();
                } catch (java.io.IOException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            popup.add(mi);
        }
        popup.show(comp, x, y);
    }

    public void refresh() {
        var structural = structuralIds();
        var rows       = new ArrayList<FlatRow>();
        for (var group : new BlockedLens().groups(workspace)) {
            rows.add(new FlatRow(true, group.blocker(), null, null));
            for (var action : group.blocked()) {
                var parent = workspace.getParent(action.getId())
                        .filter(p -> !structural.contains(p.getId()))
                        .orElse(null);
                var path = parent != null ? buildProjectPath(parent.getId(), structural) : null;
                rows.add(new FlatRow(false, action, parent != null ? parent.getId() : null, path));
            }
        }
        tableModel.setRows(rows);
        UiHelper.setTableEmpty(tableCard, rows.isEmpty());
    }

    private String buildProjectPath(UUID projectId, Set<UUID> structural) {
        var path = workspace.buildPath(projectId);
        var sb   = new StringBuilder();
        for (var node : path) {
            if (structural.contains(node.getId())) continue;
            if (!sb.isEmpty()) sb.append(" > ");
            sb.append(node.getTitle());
        }
        return sb.toString();
    }

    private Set<UUID> structuralIds() {
        return Stream.of(
                workspace.getRootNodeId(), workspace.getInboxNodeId(),
                workspace.getProjectsNodeId(), workspace.getNextActionsNodeId()
        ).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private final class BlockedTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Action", "Project"};
        private List<FlatRow> rows = List.of();

        void setRows(List<FlatRow> rows) { this.rows = rows; fireTableDataChanged(); }
        FlatRow getRow(int i) { return rows.get(i); }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }
        @Override public boolean isCellEditable(int row, int col) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            var r = rows.get(row);
            return switch (col) {
                case 0 -> r.node().getTitle();
                case 1 -> r.header() ? "" : (r.projectPath() != null ? r.projectPath() : "");
                default -> null;
            };
        }
    }
}
