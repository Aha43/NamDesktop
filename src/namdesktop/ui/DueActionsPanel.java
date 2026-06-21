package namdesktop.ui;

import namdesktop.lens.DueItemRow;
import namdesktop.lens.DueLens;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class DueActionsPanel extends JPanel {

    private record FlatRow(boolean header, String sectionLabel, Color sectionColor, DueItemRow item) {}

    private static final Color HEADER_OVERDUE   = new Color(200, 60,  60);
    private static final Color HEADER_TODAY     = new Color(180, 120,  0);
    private static final Color HEADER_THIS_WEEK = new Color( 60, 100, 180);
    private static final Color HEADER_LATER     = null; // default text

    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final Consumer<UUID> onOpenProject;
    private final DueTableModel tableModel;
    private final JTable table;
    private final JPanel tableCard;

    public DueActionsPanel(NamWorkspace workspace, NamWorkspaceService service, Consumer<UUID> onOpenProject) {
        super(new BorderLayout());
        this.workspace     = workspace;
        this.service       = service;
        this.onOpenProject = onOpenProject;
        this.tableModel    = new DueTableModel();

        var badgeRenderer = UiHelper.actionBadgeRenderer(
                row -> tableModel.getRow(row).header() ? NodeStatus.NEXT
                        : tableModel.getRow(row).item().status());

        table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                var c = super.prepareRenderer(renderer, row, column);
                var flatRow = tableModel.getRow(row);
                if (!flatRow.header() && !isRowSelected(row))
                    c.setForeground(flatRow.item().status() == NodeStatus.DONE ? Color.GRAY : getForeground());
                return c;
            }

            @Override
            public String getToolTipText(MouseEvent e) {
                var row = rowAtPoint(e.getPoint());
                var col = columnAtPoint(e.getPoint());
                if (row < 0) return null;
                var flatRow = tableModel.getRow(row);
                if (flatRow.header()) return null;
                if (col == 0) {
                    var r = getCellRect(row, 0, false);
                    if (e.getX() >= r.x + r.width - UiHelper.ACTION_PENCIL_W)
                        return "Edit: " + flatRow.item().title();
                }
                if (col == 1) return flatRow.item().projectPath();
                return null;
            }
        };
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        ProjectPathSupport.installLinkColumn(table, 1); // clickable project path (#382)

        table.getColumn("Action").setCellRenderer((t, value, sel, foc, row, col) -> {
            var flatRow = tableModel.getRow(row);
            if (flatRow.header()) {
                var label = new JLabel("  " + flatRow.sectionLabel());
                label.setFont(label.getFont().deriveFont(Font.BOLD));
                label.setOpaque(true);
                var bg = UIManager.getColor("Table.alternateRowColor");
                label.setBackground(bg != null ? bg : t.getBackground());
                if (flatRow.sectionColor() != null) label.setForeground(flatRow.sectionColor());
                label.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
                return label;
            }
            return badgeRenderer.getTableCellRendererComponent(t, value, sel, foc, row, col);
        });

        var actionEditor = new DefaultCellEditor(new JTextField());
        actionEditor.setClickCountToStart(99);
        table.getColumn("Action").setCellEditor(actionEditor);

        table.getColumn("Tags").setCellRenderer((t, value, sel, foc, row, col) -> {
            var flatRow = tableModel.getRow(row);
            if (flatRow.header()) return emptyCell(t);
            return UiHelper.tagsRenderer().getTableCellRendererComponent(t, value, sel, foc, row, col);
        });

        table.getColumn("Due").setCellRenderer((t, value, sel, foc, row, col) -> {
            var flatRow = tableModel.getRow(row);
            if (flatRow.header()) return emptyCell(t);
            return UiHelper.dueRenderer().getTableCellRendererComponent(t, value, sel, foc, row, col);
        });

        table.getColumnModel().getColumn(0).setPreferredWidth(210);
        table.getColumnModel().getColumn(2).setPreferredWidth(110);
        table.getColumnModel().getColumn(3).setPreferredWidth(60);
        table.getColumnModel().getColumn(3).setMaxWidth(60);
        UiHelper.fillTableColumn(table, 1);

        // Bulk-select checkbox column — blank on section-header rows (#402).
        var dueBoolRenderer = table.getDefaultRenderer(Boolean.class);
        table.getColumnModel().getColumn(DueTableModel.CHECK_COL).setCellRenderer((t, value, sel, foc, row, col) ->
                tableModel.getRow(row).header() ? emptyCell(t)
                        : dueBoolRenderer.getTableCellRendererComponent(t, value, sel, foc, row, col));
        add(BulkSelect.install(table, DueTableModel.CHECK_COL, tableModel.check,
                tableModel::selectableCount, tableModel::rowIds, service, this::refresh), BorderLayout.SOUTH);

        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "openDialog");
        table.getActionMap().put("openDialog", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent ev) {
                var row = table.getSelectedRow();
                if (row < 0) return;
                var flatRow = tableModel.getRow(row);
                if (flatRow.header()) return;
                new ActionDialog(SwingUtilities.getWindowAncestor(DueActionsPanel.this),
                        flatRow.item().id(), workspace, service, true,
                        DueActionsPanel.this::refresh).setVisible(true);
            }
        });

        final int[] lastRow = {-1};
        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { lastRow[0] = table.getSelectedRow(); }

            @Override
            public void mouseClicked(MouseEvent e) {
                var row = table.rowAtPoint(e.getPoint());
                if (row < 0) return;
                var flatRow = tableModel.getRow(row);
                if (flatRow.header()) { table.clearSelection(); return; }
                var item = flatRow.item();
                var col  = table.columnAtPoint(e.getPoint());
                if (col == 1) {
                    if (e.getClickCount() == 1) {
                        var seg = ProjectPathSupport.segmentAt(table, row, col, e.getX(),
                                ProjectPathSupport.forAction(workspace, item.id()));
                        if (seg != null) onOpenProject.accept(seg);
                    }
                    return;
                }
                if (col == 0) {
                    var cellRect = table.getCellRect(row, 0, false);
                    if (e.getX() < cellRect.x + UiHelper.ACTION_BADGE_W) {
                        if (e.getClickCount() == 1)
                            showStatusPopup(row, item.id(), item.status(), e.getComponent(), e.getX(), e.getY());
                    } else if (e.getX() >= cellRect.x + cellRect.width - UiHelper.ACTION_PENCIL_W) {
                        if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                        openDialog(item.id());
                    } else if (e.getClickCount() == 1 && row == lastRow[0]) {
                        if (MonitoringModeGuard.checkAndConfirm(e.getComponent()) && table.editCellAt(row, 0)) {
                            var ed = table.getEditorComponent();
                            if (ed instanceof JTextField tf) { tf.selectAll(); tf.requestFocusInWindow(); }
                        }
                    } else if (e.getClickCount() == 2) {
                        if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                        openDialog(item.id());
                    }
                    return;
                }
                if (e.getClickCount() == 2) openDialog(item.id());
            }
        });

        tableCard = UiHelper.tableCard(new JScrollPane(table), "No actions with a due date set.");
        add(tableCard, BorderLayout.CENTER);
    }

    public void refresh() {
        var result = new DueLens().items(workspace);
        var rows   = new ArrayList<FlatRow>();
        addSection(rows, "Overdue",   HEADER_OVERDUE,   result.overdue());
        addSection(rows, "Today",     HEADER_TODAY,     result.today());
        addSection(rows, "This week", HEADER_THIS_WEEK, result.thisWeek());
        addSection(rows, "Later",     HEADER_LATER,     result.later());
        tableModel.setRows(rows);
        UiHelper.setTableEmpty(tableCard, rows.isEmpty());
    }

    private static void addSection(List<FlatRow> rows, String label, Color color, List<DueItemRow> items) {
        if (items.isEmpty()) return;
        rows.add(new FlatRow(true, label, color, null));
        items.forEach(item -> rows.add(new FlatRow(false, null, null, item)));
    }

    private void openDialog(UUID id) {
        new ActionDialog(SwingUtilities.getWindowAncestor(this), id, workspace, service, true,
                this::refresh).setVisible(true);
    }

    private void showStatusPopup(int row, UUID id, NodeStatus current, Component comp, int x, int y) {
        var popup = new JPopupMenu();
        for (var entry : new Object[][]{ {"Next", NodeStatus.NEXT}, {"Backlog", NodeStatus.BACKLOG}, {"Done", NodeStatus.DONE} }) {
            var label  = (String) entry[0];
            var target = (NodeStatus) entry[1];
            var letter = switch (target) { case NEXT -> "N"; case BACKLOG -> "B"; default -> "D"; };
            var mi     = new JMenuItem((current == target ? "✓ " : "  ") + letter + "  " + label);
            mi.setEnabled(current != target);
            mi.addActionListener(e -> {
                if (!MonitoringModeGuard.checkAndConfirm(comp)) return;
                try {
                    switch (target) {
                        case NEXT    -> service.markNext(id);
                        case BACKLOG -> service.markBacklog(id);
                        case DONE    -> service.markDone(id);
                        default      -> {}
                    }
                    refresh();
                } catch (java.io.IOException ex) {
                    JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            popup.add(mi);
        }
        popup.show(comp, x, y);
    }

    private static Component emptyCell(JTable t) {
        var label = new JLabel("");
        label.setOpaque(true);
        var bg = UIManager.getColor("Table.alternateRowColor");
        label.setBackground(bg != null ? bg : t.getBackground());
        return label;
    }

    private final class DueTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Action", "Project", "Tags", "Due", ""};
        static final int CHECK_COL = 4;
        private List<FlatRow> rows = List.of();
        final CheckColumn check = new CheckColumn();

        void setRows(List<FlatRow> rows) {
            this.rows = rows;
            check.retain(rowIds());
            fireTableDataChanged();
        }
        FlatRow getRow(int i) { return rows.get(i); }

        /** Ids of the selectable (non-header) rows. */
        List<UUID> rowIds() {
            return rows.stream().filter(r -> !r.header()).map(r -> r.item().id()).toList();
        }
        int selectableCount() { return (int) rows.stream().filter(r -> !r.header()).count(); }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }
        @Override public boolean isCellEditable(int row, int col) {
            return (col == 0 || col == CHECK_COL) && !rows.get(row).header();
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (row >= rows.size() || rows.get(row).header()) return;
            if (col == CHECK_COL) { check.set(rows.get(row).item().id(), Boolean.TRUE.equals(value)); return; }
            if (col != 0) return;
            var newTitle = value.toString().strip();
            var item = rows.get(row).item();
            if (newTitle.isEmpty() || newTitle.equals(item.title())) return;
            if (!MonitoringModeGuard.checkAndConfirm(null)) return;
            try {
                service.renameNode(item.id(), newTitle);
                refresh();
            } catch (java.io.IOException ex) {
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        @Override
        public Object getValueAt(int row, int col) {
            var flatRow = rows.get(row);
            if (flatRow.header()) return switch (col) { case 0 -> flatRow.sectionLabel(); default -> null; };
            var r = flatRow.item();
            return switch (col) {
                case 0 -> r.title();
                case 1 -> r.projectPath() != null ? r.projectPath() : "";
                case 2 -> new String[]{
                        String.join(", ", r.tags()),
                        String.join(", ", r.inheritedTags())};
                case 3 -> r.dueAt();
                case CHECK_COL -> check.isChecked(r.id());
                default -> null;
            };
        }

        @Override
        public Class<?> getColumnClass(int col) {
            if (col == 2) return String[].class;
            if (col == 3) return LocalDate.class;
            if (col == CHECK_COL) return Boolean.class;
            return String.class;
        }
    }
}
