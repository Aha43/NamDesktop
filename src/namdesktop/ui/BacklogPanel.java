package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.app.AppSettings;
import namdesktop.lens.BacklogItemRow;
import namdesktop.lens.BacklogLens;
import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import java.awt.Component;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class BacklogPanel extends JPanel {

    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final Consumer<UUID> onOpenProject;
    private final BacklogTableModel tableModel;
    private final JTable table;
    private final JScrollPane scrollPane;
    private final JPanel deckWrapper;
    private final CardLayout deckCards;
    private final JToolBar toolbar;
    private final JButton upButton;
    private final JButton downButton;
    private List<UUID> currentOrder = List.of();
    private UUID pendingSelection;
    private TableColumn statusColumn;
    private boolean statusColumnVisible = true;
    private MoonCardPanel moonCardPanel;
    private boolean showBlocked = false;
    private boolean freeOnly;
    private String sortOrder;
    private final JPanel filterStrip;
    private final Set<UUID> selectedProjects = new HashSet<>();

    public BacklogPanel(NamWorkspace workspace, NamWorkspaceService service, Consumer<UUID> onOpenProject) {
        super(new BorderLayout());
        this.workspace     = workspace;
        this.service       = service;
        this.onOpenProject = onOpenProject;
        this.tableModel    = new BacklogTableModel();
        this.freeOnly      = AppSettings.getInstance().isBacklogFreeOnly();
        this.sortOrder     = AppSettings.getInstance().getBacklogSortOrder();
        this.filterStrip   = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));

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

        var moonButton = UiHelper.iconButton("Focus mode",
                new FlatSVGIcon(BacklogPanel.class.getResource("/icons/stack-2.svg")).derive(16, 16));
        moonButton.setToolTipText("Work through this list one action at a time");
        moonButton.addActionListener(e -> enterDeckMode());

        var listIcon      = new FlatSVGIcon(BacklogPanel.class.getResource("/icons/layout-list.svg")).derive(16, 16);
        var dashboardIcon = new FlatSVGIcon(BacklogPanel.class.getResource("/icons/layout-dashboard.svg")).derive(16, 16);
        var freeOnlyBtn   = UiHelper.iconToggleButton("Free only", freeOnly ? listIcon : dashboardIcon);
        freeOnlyBtn.setSelected(freeOnly);
        freeOnlyBtn.addActionListener(e -> {
            freeOnly = freeOnlyBtn.isSelected();
            freeOnlyBtn.setIcon(freeOnly ? listIcon : dashboardIcon);
            selectedProjects.clear();
            AppSettings.getInstance().setBacklogFreeOnly(freeOnly);
            try { AppSettings.getInstance().save(); } catch (java.io.IOException ignored) {}
            refresh();
        });

        var lockIcon     = new FlatSVGIcon(BacklogPanel.class.getResource("/icons/lock.svg")).derive(16, 16);
        var lockOpenIcon = new FlatSVGIcon(BacklogPanel.class.getResource("/icons/lock-open.svg")).derive(16, 16);
        var showBlockedBtn = UiHelper.iconToggleButton("Blocked", lockIcon);
        showBlockedBtn.addActionListener(e -> {
            showBlocked = showBlockedBtn.isSelected();
            showBlockedBtn.setIcon(showBlocked ? lockOpenIcon : lockIcon);
            refresh();
        });

        var clockUpIcon   = new FlatSVGIcon(BacklogPanel.class.getResource("/icons/clock-up.svg")).derive(16, 16);
        var clockDownIcon = new FlatSVGIcon(BacklogPanel.class.getResource("/icons/clock-down.svg")).derive(16, 16);
        var sortBtn = new JToggleButton(clockUpIcon);
        sortBtn.setSelected(!sortOrder.equals("NONE"));
        sortBtn.setIcon(sortOrder.equals("LIFO") ? clockDownIcon : clockUpIcon);
        sortBtn.setToolTipText(backlogSortTooltip(sortOrder));
        sortBtn.addActionListener(e -> {
            sortOrder = switch (sortOrder) { case "NONE" -> "FIFO"; case "FIFO" -> "LIFO"; default -> "NONE"; };
            sortBtn.setSelected(!sortOrder.equals("NONE"));
            sortBtn.setIcon(sortOrder.equals("LIFO") ? clockDownIcon : clockUpIcon);
            sortBtn.setToolTipText(backlogSortTooltip(sortOrder));
            AppSettings.getInstance().setBacklogSortOrder(sortOrder);
            try { AppSettings.getInstance().save(); } catch (java.io.IOException ignored) {}
            refresh();
        });

        toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(addButton);
        toolbar.add(upButton);
        toolbar.add(downButton);
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(sortBtn);
        toolbar.add(freeOnlyBtn);
        toolbar.add(showBlockedBtn);
        toolbar.add(moonButton);

        var north = new JPanel(new BorderLayout());
        north.add(toolbar, BorderLayout.NORTH);
        north.add(filterStrip, BorderLayout.CENTER);
        filterStrip.setVisible(!freeOnly);
        add(north, BorderLayout.NORTH);

        table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                var c = super.prepareRenderer(renderer, row, column);
                var item = tableModel.getRow(row);
                var blocked = showBlocked && service.isBlocked(item.id());
                c.setForeground(item.status() == NodeStatus.DONE || blocked ? Color.GRAY : getForeground());
                int style = (column == 1 && item.isSubProject())
                        ? Font.ITALIC : Font.PLAIN;
                c.setFont(c.getFont().deriveFont(style));
                return c;
            }

            @Override
            public String getToolTipText(MouseEvent e) {
                var row = rowAtPoint(e.getPoint());
                var col = columnAtPoint(e.getPoint());
                if (row < 0) return null;
                if (col == 0) {
                    var r = getCellRect(row, 0, false);
                    if (e.getX() >= r.x + r.width - UiHelper.ACTION_PENCIL_W)
                        return "Edit: " + tableModel.getRow(row).title();
                    if (showBlocked && service.isBlocked(tableModel.getRow(row).id())) {
                        return workspace.getNode(tableModel.getRow(row).id())
                                .map(n -> n.getBlockedBy().stream()
                                        .map(workspace::getNode)
                                        .flatMap(java.util.Optional::stream)
                                        .filter(p -> p.getStatus() != NodeStatus.DONE)
                                        .map(NamNode::getTitle)
                                        .collect(Collectors.joining(", ")))
                                .filter(s -> !s.isEmpty())
                                .map(s -> "Blocked by: " + s)
                                .orElse(null);
                    }
                }
                if (col != 1) return null;
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
                if (col == 1) {
                    if (e.getClickCount() == 1 && item.parentId() != null)
                        onOpenProject.accept(item.parentId());
                    return;
                }
                if (col == 0) {
                    var cellRect = table.getCellRect(row, 0, false);
                    if (e.getX() < cellRect.x + UiHelper.ACTION_BADGE_W) {
                        if (e.getClickCount() == 1) showStatusPopup(row, e.getComponent(), e.getX(), e.getY());
                    } else if (e.getX() >= cellRect.x + cellRect.width - UiHelper.ACTION_PENCIL_W) {
                        if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                        new ActionDialog(SwingUtilities.getWindowAncestor(BacklogPanel.this),
                                item.id(), workspace, service, false, BacklogPanel.this::refresh).setVisible(true);
                    } else if (e.getClickCount() == 1 && row == lastRow[0]) {
                        if (table.editCellAt(row, 0)) {
                            var ed = table.getEditorComponent();
                            if (ed instanceof JTextField tf) { tf.selectAll(); tf.requestFocusInWindow(); }
                        }
                    } else if (e.getClickCount() == 2) {
                        if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                        new ActionDialog(SwingUtilities.getWindowAncestor(BacklogPanel.this),
                                item.id(), workspace, service, false, BacklogPanel.this::refresh).setVisible(true);
                    }
                    return;
                }
                if (e.getClickCount() == 2) {
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

        table.getColumn("Action").setCellRenderer(UiHelper.actionBadgeRenderer(
                row -> tableModel.getRow(row).status(),
                row -> showBlocked && service.isBlocked(tableModel.getRow(row).id())));
        var actionEditor = new DefaultCellEditor(new JTextField());
        actionEditor.setClickCountToStart(99);
        table.getColumn("Action").setCellEditor(actionEditor);
        table.getColumnModel().getColumn(2).setCellRenderer(UiHelper.ageRenderer(false));
        table.getColumnModel().getColumn(2).setPreferredWidth(50);
        table.getColumnModel().getColumn(2).setMaxWidth(50);
        table.getColumn("Tags").setCellRenderer(UiHelper.tagsRenderer());
        statusColumn = table.getColumnModel().getColumn(4);
        table.getColumnModel().getColumn(0).setPreferredWidth(210);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setPreferredWidth(70);
        table.getColumnModel().getColumn(4).setMaxWidth(70);
        table.getColumnModel().getColumn(5).setCellRenderer(UiHelper.paperclipRenderer());
        table.getColumnModel().getColumn(5).setPreferredWidth(18);
        table.getColumnModel().getColumn(5).setMaxWidth(18);
        UiHelper.fillTableColumn(table, 1);
        applyColumnVisibility(AppSettings.getInstance().isShowStatusColumn());

        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "openDialog");
        table.getActionMap().put("openDialog", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent ev) {
                var row = table.getSelectedRow();
                if (row < 0) return;
                var item = tableModel.getRow(row);
                new ActionDialog(SwingUtilities.getWindowAncestor(BacklogPanel.this),
                        item.id(), workspace, service, false, BacklogPanel.this::refresh).setVisible(true);
            }
        });

        scrollPane  = new JScrollPane(table);
        deckCards   = new CardLayout();
        deckWrapper = new JPanel(deckCards);
        deckWrapper.add(scrollPane,                                                                          "table");
        deckWrapper.add(UiHelper.emptyStateLabel("Nothing deferred. Items you park for later will appear here."), "empty");
        add(deckWrapper, BorderLayout.CENTER);
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

        rebuildFilterStrip(liveRows, rowById);

        var displayIds = currentOrder.stream()
                .filter(id -> showBlocked || !service.isBlocked(id))
                .filter(id -> {
                    var row = rowById.get(id);
                    if (freeOnly) return row.parentId() == null;
                    if (!selectedProjects.isEmpty()) {
                        var topLevel = topLevelProjectOf(id);
                        return topLevel != null && selectedProjects.contains(topLevel);
                    }
                    return true;
                })
                .toList();
        if (!sortOrder.equals("NONE")) {
            Comparator<LocalDateTime> dir = sortOrder.equals("FIFO")
                    ? Comparator.naturalOrder() : Comparator.reverseOrder();
            Comparator<UUID> cmp = Comparator.comparing(
                    id -> { var r = rowById.get(id); return r.updatedAt() != null ? r.updatedAt() : r.createdAt(); },
                    Comparator.nullsLast(dir));
            displayIds = displayIds.stream().sorted(cmp).toList();
        }
        var sortActive = !sortOrder.equals("NONE");
        upButton.setVisible(!sortActive);
        downButton.setVisible(!sortActive);
        tableModel.setRows(displayIds.stream().map(rowById::get).toList());
        if (moonCardPanel == null)
            deckCards.show(deckWrapper, displayIds.isEmpty() ? "empty" : "table");

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

    private void rebuildFilterStrip(List<BacklogItemRow> liveRows, Map<UUID, BacklogItemRow> rowById) {
        filterStrip.setVisible(!freeOnly);
        if (freeOnly) { filterStrip.revalidate(); filterStrip.repaint(); return; }

        var projectsNodeId = workspace.getProjectsNodeId();
        Map<UUID, String> topProjects = new LinkedHashMap<>();
        for (var id : currentOrder) {
            var row = rowById.get(id);
            if (row.parentId() == null) continue;
            var top = topLevelProjectOf(id);
            if (top != null && !topProjects.containsKey(top))
                workspace.getNode(top).ifPresent(n -> topProjects.put(top, n.getTitle()));
        }

        filterStrip.removeAll();
        selectedProjects.retainAll(topProjects.keySet());
        for (var entry : topProjects.entrySet()) {
            var id    = entry.getKey();
            var chip  = new JToggleButton(entry.getValue());
            chip.setSelected(selectedProjects.contains(id));
            chip.setFont(chip.getFont().deriveFont(11f));
            chip.addActionListener(e -> {
                if (chip.isSelected()) selectedProjects.add(id); else selectedProjects.remove(id);
                refresh();
            });
            filterStrip.add(chip);
        }
        filterStrip.revalidate();
        filterStrip.repaint();
    }

    private UUID topLevelProjectOf(UUID nodeId) {
        var projectsNodeId = workspace.getProjectsNodeId();
        var path = workspace.buildPath(nodeId);
        UUID result = null;
        for (var node : path) {
            if (workspace.getParent(node.getId())
                    .map(p -> p.getId().equals(projectsNodeId)).orElse(false)) {
                result = node.getId();
                break;
            }
        }
        return result;
    }

    private void showStatusPopup(int row, Component comp, int x, int y) {
        var id      = tableModel.getRow(row).id();
        var current = tableModel.getRow(row).status();
        var popup   = new JPopupMenu();
        for (var entry : new Object[][]{ {"Next", NodeStatus.NEXT}, {"Backlog", NodeStatus.BACKLOG}, {"Done", NodeStatus.DONE} }) {
            var label  = (String) entry[0];
            var target = (NodeStatus) entry[1];
            var letter = switch (target) { case NEXT -> "N"; case BACKLOG -> "B"; default -> "D"; };
            var mi     = new JMenuItem((current == target ? "✓ " : "  ") + letter + "  " + label);
            mi.setEnabled(current != target && !(target == NodeStatus.DONE && service.isBlocked(id)));
            mi.addActionListener(e -> {
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
                    pendingSelection = id;
                    refresh();
                } catch (java.io.IOException ex) { showError(ex.getMessage()); }
            });
            popup.add(mi);
        }
        popup.show(comp, x, y);
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

    private static String backlogSortTooltip(String order) {
        return switch (order) { case "FIFO" -> "Newest first"; case "LIFO" -> "Remove sort"; default -> "Oldest first"; };
    }

    private void enterDeckMode() {
        var cards = new java.util.ArrayList<MoonCardPanel.Card>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            var row = tableModel.getRow(i);
            var desc = workspace.getNode(row.id()).map(n -> n.getDescription()).orElse(null);
            cards.add(new MoonCardPanel.Card(row.id(), row.title(), desc, row.projectPath()));
        }
        if (cards.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No actions to show.", "Focus mode", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (moonCardPanel != null) deckWrapper.remove(moonCardPanel);
        moonCardPanel = new MoonCardPanel(cards, service, this::exitDeckMode);
        deckWrapper.add(moonCardPanel, "moon");
        deckCards.show(deckWrapper, "moon");
        toolbar.setVisible(false);
    }

    private void exitDeckMode() {
        if (moonCardPanel == null) return;
        toolbar.setVisible(true);
        refresh();
        var old = moonCardPanel;
        moonCardPanel = null;
        SwingUtilities.invokeLater(() -> deckWrapper.remove(old));
    }

    private final class BacklogTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Action", "Project", "Age", "Tags", "Status", ""};
        private List<BacklogItemRow> rows = List.of();

        void setRows(List<BacklogItemRow> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        BacklogItemRow getRow(int index) { return rows.get(index); }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }
        @Override public boolean isCellEditable(int row, int col) { return col == 0; }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col != 0 || row >= rows.size()) return;
            var newTitle = value.toString().strip();
            var r = rows.get(row);
            if (newTitle.isEmpty() || newTitle.equals(r.title())) return;
            try {
                pendingSelection = r.id();
                service.renameNode(r.id(), newTitle);
                refresh();
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
                case 2 -> UiHelper.ageDays(r.updatedAt(), r.createdAt());
                case 3 -> new String[]{
                        String.join(", ", r.tags()),
                        String.join(", ", r.inheritedTags())};
                case 4 -> r.status();
                case 5 -> r.hasResources();
                default -> null;
            };
        }

        @Override
        public Class<?> getColumnClass(int col) {
            if (col == 2) return Long.class;
            if (col == 3) return String[].class;
            if (col == 5) return Boolean.class;
            return String.class;
        }
    }
}
