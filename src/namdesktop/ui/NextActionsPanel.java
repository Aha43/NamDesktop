package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.app.AppSettings;
import namdesktop.lens.NextActionItemRow;
import namdesktop.lens.NextActionsLens;
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
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class NextActionsPanel extends JPanel {

    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final Consumer<UUID> onOpenProject;
    private final NextActionsTableModel tableModel;
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

    public NextActionsPanel(NamWorkspace workspace, NamWorkspaceService service, Consumer<UUID> onOpenProject) {
        super(new BorderLayout());
        this.workspace     = workspace;
        this.service       = service;
        this.onOpenProject = onOpenProject;
        this.tableModel    = new NextActionsTableModel();

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

        var moonButton = UiHelper.iconButton("Focus mode",
                new FlatSVGIcon(NextActionsPanel.class.getResource("/icons/stack-2.svg")).derive(16, 16));
        moonButton.setToolTipText("Work through this list one action at a time");
        moonButton.addActionListener(e -> enterDeckMode());

        var lockIcon     = new FlatSVGIcon(NextActionsPanel.class.getResource("/icons/lock.svg")).derive(16, 16);
        var lockOpenIcon = new FlatSVGIcon(NextActionsPanel.class.getResource("/icons/lock-open.svg")).derive(16, 16);
        var showBlockedBtn = new JToggleButton(lockIcon);
        showBlockedBtn.setToolTipText("Blocked actions hidden — click to show");
        showBlockedBtn.addActionListener(e -> {
            showBlocked = showBlockedBtn.isSelected();
            showBlockedBtn.setIcon(showBlocked ? lockOpenIcon : lockIcon);
            showBlockedBtn.setToolTipText(showBlocked
                    ? "Blocked actions visible — click to hide"
                    : "Blocked actions hidden — click to show");
            refresh();
        });

        toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(addButton);
        toolbar.add(upButton);
        toolbar.add(downButton);
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(showBlockedBtn);
        toolbar.add(moonButton);
        add(toolbar, BorderLayout.NORTH);

        table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                var c = super.prepareRenderer(renderer, row, column);
                var item = tableModel.getRow(row);
                var blocked = showBlocked && service.isBlocked(item.id());
                c.setForeground(item.status() == NodeStatus.DONE || blocked ? Color.GRAY : getForeground());
                if (column == 1 && item.isSubProject()) {
                    c.setFont(c.getFont().deriveFont(Font.ITALIC));
                } else {
                    c.setFont(c.getFont().deriveFont(Font.PLAIN));
                }
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
                        new ActionDialog(SwingUtilities.getWindowAncestor(NextActionsPanel.this),
                                item.id(), workspace, service, true, NextActionsPanel.this::refresh).setVisible(true);
                    } else if (e.getClickCount() == 1 && row == lastRow[0]) {
                        if (table.editCellAt(row, 0)) {
                            var ed = table.getEditorComponent();
                            if (ed instanceof JTextField tf) { tf.selectAll(); tf.requestFocusInWindow(); }
                        }
                    } else if (e.getClickCount() == 2) {
                        if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                        new ActionDialog(SwingUtilities.getWindowAncestor(NextActionsPanel.this),
                                item.id(), workspace, service, true, NextActionsPanel.this::refresh).setVisible(true);
                    }
                    return;
                }
                if (e.getClickCount() == 2) {
                    new ActionDialog(SwingUtilities.getWindowAncestor(NextActionsPanel.this),
                            item.id(), workspace, service, true, NextActionsPanel.this::refresh).setVisible(true);
                }
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

        table.getColumn("Action").setCellRenderer(UiHelper.actionBadgeRenderer(
                row -> tableModel.getRow(row).status(),
                row -> showBlocked && service.isBlocked(tableModel.getRow(row).id())));
        var actionEditor = new DefaultCellEditor(new JTextField());
        actionEditor.setClickCountToStart(99);
        table.getColumn("Action").setCellEditor(actionEditor);
        table.getColumn("Tags").setCellRenderer(UiHelper.tagsRenderer());
        statusColumn = table.getColumnModel().getColumn(3);
        table.getColumnModel().getColumn(0).setPreferredWidth(210);
        table.getColumnModel().getColumn(2).setPreferredWidth(110);
        table.getColumnModel().getColumn(3).setPreferredWidth(70);
        table.getColumnModel().getColumn(3).setMaxWidth(70);
        UiHelper.fillTableColumn(table, 1);
        applyColumnVisibility(AppSettings.getInstance().isShowStatusColumn());

        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "openDialog");
        table.getActionMap().put("openDialog", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent ev) {
                var row = table.getSelectedRow();
                if (row < 0) return;
                var item = tableModel.getRow(row);
                new ActionDialog(SwingUtilities.getWindowAncestor(NextActionsPanel.this),
                        item.id(), workspace, service, true, NextActionsPanel.this::refresh).setVisible(true);
            }
        });

        scrollPane  = new JScrollPane(table);
        deckCards   = new CardLayout();
        deckWrapper = new JPanel(deckCards);
        deckWrapper.add(scrollPane,                                                         "table");
        deckWrapper.add(UiHelper.emptyStateLabel("No next actions. Open the Inbox to process items, or add one directly."), "empty");
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
        var liveRows   = new NextActionsLens().items(workspace);
        var liveNodes  = liveRows.stream()
                .map(r -> workspace.getNode(r.id()).orElseThrow())
                .toList();
        var ordered    = service.getViewOrder(NamWorkspaceService.VIEW_NEXT_ACTIONS, liveNodes);
        currentOrder  = ordered.stream().map(NamNode::getId).toList();
        var rowById   = liveRows.stream().collect(Collectors.toMap(NextActionItemRow::id, r -> r));
        var displayIds = showBlocked
                ? currentOrder
                : currentOrder.stream().filter(id -> !service.isBlocked(id)).toList();
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
            service.createNextAction(title.strip());
            refresh();
        } catch (java.io.IOException ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
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

    private final class NextActionsTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Action", "Project", "Tags", "Status"};
        private List<NextActionItemRow> rows = List.of();

        void setRows(List<NextActionItemRow> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        NextActionItemRow getRow(int index) { return rows.get(index); }

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
                case 2 -> new String[]{
                        String.join(", ", r.tags()),
                        String.join(", ", r.inheritedTags())};
                case 3 -> r.status();
                default -> null;
            };
        }

        @Override
        public Class<?> getColumnClass(int col) {
            return col == 2 ? String[].class : String.class;
        }
    }
}
