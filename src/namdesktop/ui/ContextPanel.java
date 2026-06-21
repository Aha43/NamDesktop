package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.app.AppSettings;
import namdesktop.lens.ContextItemRow;
import namdesktop.lens.ContextLens;
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
import java.util.UUID;
import java.util.stream.Collectors;

public final class ContextPanel extends JPanel {

    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final Runnable onViewCreated;
    private final java.util.function.Consumer<java.util.UUID> onOpenProject;
    private final ContextTableModel tableModel;
    private JPanel tableCard;
    private final JPanel tagSelectorPanel;
    private final List<JCheckBox> tagBoxes = new ArrayList<>();
    private JLabel matchLabel;
    private JButton addActionButton;
    private JButton saveViewButton;
    private JButton clearButton;

    public ContextPanel(NamWorkspace workspace, NamWorkspaceService service, Runnable onViewCreated,
                        java.util.function.Consumer<java.util.UUID> onOpenProject) {
        super(new BorderLayout());
        this.workspace      = workspace;
        this.service        = service;
        this.onViewCreated  = onViewCreated;
        this.onOpenProject  = onOpenProject;
        this.tableModel     = new ContextTableModel();

        matchLabel = new JLabel("0 matches");

        clearButton = UiHelper.iconButton("Clear",
                new FlatSVGIcon(ContextPanel.class.getResource("/icons/eraser.svg")).derive(16, 16));
        clearButton.setToolTipText("Clear all selected tags");
        clearButton.setEnabled(false);
        clearButton.addActionListener(e -> {
            tagBoxes.forEach(b -> b.setSelected(false));
            refreshResults();
        });

        addActionButton = UiHelper.iconButton("Add action", new FlatSVGIcon(ContextPanel.class.getResource("/icons/plus.svg")).derive(16, 16));
        addActionButton.setEnabled(false);
        addActionButton.addActionListener(e -> addTaggedAction());

        saveViewButton = UiHelper.iconButton("Save as view…",
                new FlatSVGIcon(ContextPanel.class.getResource("/icons/bookmark.svg")).derive(16, 16));
        saveViewButton.setToolTipText("Save current tag filter as a named view");
        saveViewButton.setEnabled(false);
        saveViewButton.addActionListener(e -> saveCurrentView());

        var eastButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        eastButtons.add(addActionButton);
        eastButtons.add(saveViewButton);
        eastButtons.add(clearButton);

        var selectorHeader = new JPanel(new BorderLayout());
        selectorHeader.add(matchLabel,  BorderLayout.WEST);
        selectorHeader.add(eastButtons, BorderLayout.EAST);

        tagSelectorPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));
        tagSelectorPanel.setBorder(BorderFactory.createTitledBorder("Filter by tags (AND)"));

        var table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                var c = super.prepareRenderer(renderer, row, column);
                var status = tableModel.getRow(row).status();
                c.setForeground(status == NodeStatus.DONE ? Color.GRAY : getForeground());
                return c;
            }

            @Override
            public String getToolTipText(MouseEvent e) {
                var row = rowAtPoint(e.getPoint());
                var col = columnAtPoint(e.getPoint());
                if (row < 0 || col != 0) return null;
                var r = getCellRect(row, 0, false);
                if (e.getX() >= r.x + r.width - UiHelper.ACTION_PENCIL_W)
                    return "Edit: " + tableModel.getRow(row).title();
                return null;
            }
        };
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getColumn("Action").setCellRenderer(UiHelper.actionBadgeRenderer(row -> tableModel.getRow(row).status()));
        var actionEditor = new DefaultCellEditor(new JTextField());
        actionEditor.setClickCountToStart(99);
        table.getColumn("Action").setCellEditor(actionEditor);
        table.getColumnModel().getColumn(2).setCellRenderer(UiHelper.dueRenderer());
        table.getColumnModel().getColumn(2).setPreferredWidth(60);
        table.getColumnModel().getColumn(2).setMaxWidth(60);
        table.getColumn("Tags").setCellRenderer(UiHelper.tagsRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(210);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setCellRenderer(UiHelper.paperclipRenderer());
        table.getColumnModel().getColumn(4).setPreferredWidth(18);
        table.getColumnModel().getColumn(4).setMaxWidth(18);
        UiHelper.fillTableColumn(table, 1);
        ProjectPathSupport.installLinkColumn(table, 1); // clickable project path (#382)

        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "openDialog");
        table.getActionMap().put("openDialog", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent ev) {
                var row = table.getSelectedRow();
                if (row < 0) return;
                var item = tableModel.getRow(row);
                new ActionDialog(SwingUtilities.getWindowAncestor(ContextPanel.this),
                        item.id(), workspace, service, true, ContextPanel.this::refreshResults).setVisible(true);
            }
        });

        final int[] lastRow = {-1};
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastRow[0] = table.getSelectedRow();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                var row = table.rowAtPoint(e.getPoint());
                var col = table.columnAtPoint(e.getPoint());
                if (row < 0) return;
                var item = tableModel.getRow(row);
                if (col == 0) {
                    var cellRect = table.getCellRect(row, 0, false);
                    if (e.getX() < cellRect.x + UiHelper.ACTION_BADGE_W) {
                        if (e.getClickCount() == 1) showStatusPopup(row, item.id(), item.status(), e.getComponent(), e.getX(), e.getY());
                    } else if (e.getX() >= cellRect.x + cellRect.width - UiHelper.ACTION_PENCIL_W) {
                        if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                        new ActionDialog(SwingUtilities.getWindowAncestor(ContextPanel.this),
                                item.id(), workspace, service, true, ContextPanel.this::refreshResults).setVisible(true);
                    } else if (e.getClickCount() == 1 && row == lastRow[0] && AppSettings.getInstance().isClickToRename()) {
                        if (MonitoringModeGuard.checkAndConfirm(e.getComponent()) && table.editCellAt(row, 0)) {
                            var ed = table.getEditorComponent();
                            if (ed instanceof JTextField tf) { tf.selectAll(); tf.requestFocusInWindow(); }
                        }
                    } else if (e.getClickCount() == 2) {
                        if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                        new ActionDialog(SwingUtilities.getWindowAncestor(ContextPanel.this),
                                item.id(), workspace, service, true, ContextPanel.this::refreshResults).setVisible(true);
                    }
                    return;
                }
                if (col == 1 && e.getClickCount() == 1) {
                    var seg = ProjectPathSupport.segmentAt(table, row, col, e.getX(),
                            ProjectPathSupport.forAction(workspace, item.id()));
                    if (seg != null) onOpenProject.accept(seg);
                    return;
                }
                if (e.getClickCount() == 2) {
                    new ActionDialog(SwingUtilities.getWindowAncestor(ContextPanel.this),
                            item.id(), workspace, service, true, ContextPanel.this::refreshResults).setVisible(true);
                }
            }
        });

        var northPanel = new JPanel(new BorderLayout());
        northPanel.add(selectorHeader,  BorderLayout.NORTH);
        northPanel.add(tagSelectorPanel, BorderLayout.CENTER);

        tableCard = UiHelper.tableCard(new JScrollPane(table), "No actions match the current filter.");
        add(northPanel,  BorderLayout.NORTH);
        add(tableCard,   BorderLayout.CENTER);

        add(BulkSelect.install(table, ContextTableModel.CHECK_COL, tableModel.check,
                tableModel::getRowCount, tableModel::rowIds, service, this::refreshResults), BorderLayout.SOUTH);
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
                    refreshResults();
                } catch (java.io.IOException ex) {
                    JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            popup.add(mi);
        }
        popup.show(comp, x, y);
    }

    public void refresh() {
        rebuildTagSelector();
        refreshResults();
    }

    private void rebuildTagSelector() {
        var previouslyChecked = tagBoxes.stream()
                .filter(JCheckBox::isSelected)
                .map(JCheckBox::getText)
                .collect(Collectors.toSet());

        tagSelectorPanel.removeAll();
        tagBoxes.clear();

        for (var tag : workspace.allTags()) {
            var box = new JCheckBox(tag, previouslyChecked.contains(tag));
            box.addActionListener(e -> refreshResults());
            tagBoxes.add(box);
            tagSelectorPanel.add(box);
        }

        if (tagBoxes.isEmpty()) {
            tagSelectorPanel.add(new JLabel("No tags defined yet — add tags to actions or projects"));
        }

        tagSelectorPanel.revalidate();
        tagSelectorPanel.repaint();
    }

    private void refreshResults() {
        var selected = tagBoxes.stream()
                .filter(JCheckBox::isSelected)
                .map(JCheckBox::getText)
                .toList();
        var rows = new ContextLens().items(workspace, selected);
        tableModel.setRows(rows);
        UiHelper.setTableEmpty(tableCard, rows.isEmpty());
        matchLabel.setText(rows.size() + (rows.size() == 1 ? " match" : " matches"));
        var hasFilter = !selected.isEmpty();
        addActionButton.setEnabled(hasFilter);
        saveViewButton.setEnabled(hasFilter);
        clearButton.setEnabled(hasFilter);
    }

    private void addTaggedAction() {
        if (!MonitoringModeGuard.checkAndConfirm(this)) return;
        var selected = tagBoxes.stream()
                .filter(JCheckBox::isSelected)
                .map(JCheckBox::getText)
                .toList();
        var title = JOptionPane.showInputDialog(this, "Action title:", "Add action", JOptionPane.PLAIN_MESSAGE);
        if (title == null || title.isBlank()) return;
        try {
            service.createNextAction(title.strip(), selected);
            refreshResults();
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveCurrentView() {
        var selected = tagBoxes.stream()
                .filter(JCheckBox::isSelected)
                .map(JCheckBox::getText)
                .toList();
        var nameField  = new JTextField(20);
        var nextCheck  = new JCheckBox("Next actions only");
        var panel      = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        panel.add(new JLabel("View name:"));
        panel.add(nameField);
        panel.add(nextCheck);
        var result = JOptionPane.showConfirmDialog(this, panel, "Save as view",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        var name = nameField.getText();
        if (name == null || name.isBlank()) return;
        try {
            service.createSavedView(name, selected, nextCheck.isSelected());
            onViewCreated.run();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot save view", JOptionPane.ERROR_MESSAGE);
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private final class ContextTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Action", "Project", "Due", "Tags", "", ""};
        static final int CHECK_COL = 5;
        private List<ContextItemRow> rows = List.of();
        final CheckColumn check = new CheckColumn();

        void setRows(List<ContextItemRow> rows) {
            this.rows = rows;
            check.retain(rows.stream().map(ContextItemRow::id).toList());
            fireTableDataChanged();
        }

        ContextItemRow getRow(int index) { return rows.get(index); }
        List<UUID> rowIds() { return rows.stream().map(ContextItemRow::id).toList(); }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }
        @Override public boolean isCellEditable(int row, int col) { return col == 0 || col == CHECK_COL; }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == CHECK_COL) { if (row < rows.size()) check.set(rows.get(row).id(), Boolean.TRUE.equals(value)); return; }
            if (col != 0 || row >= rows.size()) return;
            var newTitle = value.toString().strip();
            var r = rows.get(row);
            if (newTitle.isEmpty() || newTitle.equals(r.title())) return;
            if (!MonitoringModeGuard.checkAndConfirm(null)) return;
            try {
                service.renameNode(r.id(), newTitle);
                ContextPanel.this.refreshResults();
            } catch (java.io.IOException ex) {
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        @Override
        public Object getValueAt(int row, int col) {
            var r = rows.get(row);
            return switch (col) {
                case 0 -> r.title();
                case 1 -> r.projectPath() != null ? r.projectPath() : "";
                case 2 -> r.dueAt();
                case 3 -> new String[]{
                        String.join(", ", r.tags()),
                        String.join(", ", r.inheritedTags())};
                case 4 -> r.hasResources();
                case CHECK_COL -> check.isChecked(r.id());
                default -> null;
            };
        }

        @Override
        public Class<?> getColumnClass(int col) {
            if (col == 2) return java.time.LocalDate.class;
            if (col == 3) return String[].class;
            if (col == 4 || col == CHECK_COL) return Boolean.class;
            return String.class;
        }
    }

    // FlowLayout that wraps to next line when out of space
    private static final class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            return layoutSize(target, false);
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                var targetWidth = target.getSize().width;
                if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;
                var insets = target.getInsets();
                var maxWidth = targetWidth - insets.left - insets.right - getHgap() * 2;
                var dim = new Dimension(0, 0);
                var rowWidth = 0;
                var rowHeight = 0;
                for (int i = 0; i < target.getComponentCount(); i++) {
                    var c = target.getComponent(i);
                    if (!c.isVisible()) continue;
                    var d = preferred ? c.getPreferredSize() : c.getMinimumSize();
                    if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                        dim.width = Math.max(dim.width, rowWidth);
                        dim.height += rowHeight + getVgap();
                        rowWidth = 0;
                        rowHeight = 0;
                    }
                    rowWidth += d.width + getHgap();
                    rowHeight = Math.max(rowHeight, d.height);
                }
                dim.width = Math.max(dim.width, rowWidth);
                dim.height += rowHeight + getVgap() * 2 + insets.top + insets.bottom;
                return dim;
            }
        }
    }
}
