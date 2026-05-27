package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.lens.ContextItemRow;
import namdesktop.lens.ContextLens;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.model.SavedView;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class SavedViewPanel extends JPanel {

    private final SavedView view;
    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final Runnable onDeleted;
    private final Consumer<String> onRenamed;
    private final ViewTableModel tableModel;
    private final JScrollPane scrollPane;
    private final JPanel deckWrapper;
    private final CardLayout deckCards;
    private final JButton addActionButton;
    private final JButton renameButton;
    private final JButton deleteButton;
    private MoonCardPanel moonCardPanel;

    public SavedViewPanel(SavedView view, NamWorkspace workspace, NamWorkspaceService service,
                          Runnable onDeleted, Consumer<String> onRenamed) {
        super(new BorderLayout());
        this.view       = view;
        this.workspace  = workspace;
        this.service    = service;
        this.onDeleted  = onDeleted;
        this.onRenamed  = onRenamed;
        this.tableModel = new ViewTableModel();

        var nameLabel = new JLabel(view.name());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));

        addActionButton = UiHelper.iconButton("Add action", new FlatSVGIcon(SavedViewPanel.class.getResource("/icons/plus.svg")).derive(16, 16));
        addActionButton.addActionListener(e -> addTaggedAction());

        renameButton = UiHelper.iconButton("Rename filter", new FlatSVGIcon(SavedViewPanel.class.getResource("/icons/cursor-text.svg")).derive(16, 16));
        renameButton.setToolTipText("Rename this filter");
        renameButton.addActionListener(e -> renameView());

        deleteButton = UiHelper.iconButton("Delete filter", new FlatSVGIcon(SavedViewPanel.class.getResource("/icons/trash.svg")).derive(16, 16));
        deleteButton.setToolTipText("Delete this filter");
        deleteButton.addActionListener(e -> deleteView());

        var moonButton = UiHelper.iconButton("Focus mode",
                new FlatSVGIcon(SavedViewPanel.class.getResource("/icons/stack-2.svg")).derive(16, 16));
        moonButton.setToolTipText("Work through this list one action at a time");
        moonButton.addActionListener(e -> enterDeckMode());

        var eastButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        eastButtons.add(moonButton);
        eastButtons.add(addActionButton);
        eastButtons.add(renameButton);
        eastButtons.add(deleteButton);

        var header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(4, 6, 2, 6));
        header.add(nameLabel,   BorderLayout.WEST);
        header.add(eastButtons, BorderLayout.EAST);

        var tagsText  = "Tags: " + (view.tags().isEmpty() ? "(any)" : String.join(", ", view.tags()));
        var filterText = view.nextOnly() ? tagsText + " · Next only" : tagsText;
        var tagsLabel = new JLabel(filterText);
        tagsLabel.setBorder(BorderFactory.createEmptyBorder(0, 6, 4, 6));

        var northPanel = new JPanel(new BorderLayout());
        northPanel.add(header,    BorderLayout.NORTH);
        northPanel.add(tagsLabel, BorderLayout.CENTER);

        var table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                var c = super.prepareRenderer(renderer, row, column);
                c.setForeground(tableModel.getRow(row).status() == NodeStatus.DONE
                        ? Color.GRAY : getForeground());
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
        table.getColumn("Tags").setCellRenderer(UiHelper.tagsRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(210);
        table.getColumnModel().getColumn(2).setPreferredWidth(110);
        UiHelper.fillTableColumn(table, 1);

        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "openDialog");
        table.getActionMap().put("openDialog", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent ev) {
                var row = table.getSelectedRow();
                if (row < 0) return;
                var item = tableModel.getRow(row);
                new ActionDialog(SwingUtilities.getWindowAncestor(SavedViewPanel.this),
                        item.id(), workspace, service, true, SavedViewPanel.this::refresh).setVisible(true);
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
                        new ActionDialog(SwingUtilities.getWindowAncestor(SavedViewPanel.this),
                                item.id(), workspace, service, true, SavedViewPanel.this::refresh).setVisible(true);
                    } else if (e.getClickCount() == 1 && row == lastRow[0]) {
                        if (table.editCellAt(row, 0)) {
                            var ed = table.getEditorComponent();
                            if (ed instanceof JTextField tf) { tf.selectAll(); tf.requestFocusInWindow(); }
                        }
                    } else if (e.getClickCount() == 2) {
                        if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                        new ActionDialog(SwingUtilities.getWindowAncestor(SavedViewPanel.this),
                                item.id(), workspace, service, true, SavedViewPanel.this::refresh).setVisible(true);
                    }
                    return;
                }
                if (e.getClickCount() == 2) {
                    new ActionDialog(SwingUtilities.getWindowAncestor(SavedViewPanel.this),
                            item.id(), workspace, service, true, SavedViewPanel.this::refresh).setVisible(true);
                }
            }
        });

        scrollPane  = new JScrollPane(table);
        deckCards   = new CardLayout();
        deckWrapper = new JPanel(deckCards);
        deckWrapper.add(scrollPane, "table");

        add(northPanel,  BorderLayout.NORTH);
        add(deckWrapper, BorderLayout.CENTER);

        refresh();
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
                try {
                    switch (target) {
                        case NEXT    -> service.markNext(id);
                        case BACKLOG -> service.markBacklog(id);
                        case DONE    -> service.markDone(id);
                        default      -> {}
                    }
                    refresh();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            popup.add(mi);
        }
        popup.show(comp, x, y);
    }

    public void refresh() {
        tableModel.setRows(new ContextLens().items(workspace, view.tags(), view.nextOnly()));
    }

    private void enterDeckMode() {
        var rows  = new java.util.ArrayList<MoonCardPanel.Card>();
        var count = tableModel.getRowCount();
        for (int i = 0; i < count; i++) {
            var row  = tableModel.getRow(i);
            var desc = workspace.getNode(row.id()).map(n -> n.getDescription()).orElse(null);
            rows.add(new MoonCardPanel.Card(row.id(), row.title(), desc, row.projectPath()));
        }
        if (rows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No actions to show.", "Focus mode", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (moonCardPanel != null) deckWrapper.remove(moonCardPanel);
        moonCardPanel = new MoonCardPanel(rows, service, this::exitDeckMode);
        deckWrapper.add(moonCardPanel, "moon");
        deckCards.show(deckWrapper, "moon");
        addActionButton.setVisible(false);
        renameButton.setVisible(false);
        deleteButton.setVisible(false);
    }

    private void exitDeckMode() {
        if (moonCardPanel == null) return;
        deckCards.show(deckWrapper, "table");
        addActionButton.setVisible(true);
        renameButton.setVisible(true);
        deleteButton.setVisible(true);
        refresh();
        var old = moonCardPanel;
        moonCardPanel = null;
        SwingUtilities.invokeLater(() -> deckWrapper.remove(old));
    }

    private void addTaggedAction() {
        var title = JOptionPane.showInputDialog(this, "Action title:", "Add action", JOptionPane.PLAIN_MESSAGE);
        if (title == null || title.isBlank()) return;
        try {
            service.createNextAction(title.strip(), view.tags());
            refresh();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void renameView() {
        var newName = (String) JOptionPane.showInputDialog(this,
                "New name:", "Rename filter", JOptionPane.PLAIN_MESSAGE, null, null, view.name());
        if (newName == null || newName.isBlank() || newName.strip().equals(view.name())) return;
        try {
            service.renameSavedView(view.name(), newName.strip());
            onRenamed.accept(newName.strip());
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteView() {
        var confirm = JOptionPane.showConfirmDialog(this,
                "Delete saved filter \"" + view.name() + "\"?",
                "Delete filter", JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) return;
        try {
            service.deleteSavedView(view.name());
            onDeleted.run();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private final class ViewTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Action", "Project", "Tags"};
        private List<ContextItemRow> rows = List.of();

        void setRows(List<ContextItemRow> rows) { this.rows = rows; fireTableDataChanged(); }
        ContextItemRow getRow(int i) { return rows.get(i); }

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
                service.renameNode(r.id(), newTitle);
                SavedViewPanel.this.refresh();
            } catch (IOException ex) {
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
                default -> null;
            };
        }

        @Override
        public Class<?> getColumnClass(int col) {
            return col == 2 ? String[].class : String.class;
        }
    }
}
