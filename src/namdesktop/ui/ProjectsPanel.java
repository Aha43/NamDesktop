package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.app.AppSettings;
import namdesktop.lens.ProjectItemRow;
import namdesktop.lens.ProjectsLens;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class ProjectsPanel extends JPanel {

    private final NamWorkspace       workspace;
    private final NamWorkspaceService service;
    private final Consumer<UUID>     onOpenProject;
    private final ProjectsTableModel tableModel;
    private final List<JCheckBox>    tagBoxes    = new ArrayList<>();
    private       JPanel             filterPanel;
    private       JLabel             matchLabel;
    private       JButton            clearButton;
    private JPanel tableCard;

    // View trio: LIST (compact table) / COLUMNS / READINESS. The board views are rendered by an
    // embedded board-only ProjectWorkbenchPanel pointed at the Projects node.
    private final JPanel          center  = new JPanel(new BorderLayout());
    private JToggleButton         listButton;
    private JToggleButton         columnsButton;
    private JToggleButton         readinessButton;
    private ProjectWorkbenchPanel board;
    private String                viewMode;
    private boolean               showArchived = false;  // #407

    public ProjectsPanel(NamWorkspace workspace, NamWorkspaceService service, Consumer<UUID> onOpenProject) {
        super(new BorderLayout());
        this.workspace     = workspace;
        this.service       = service;
        this.onOpenProject = onOpenProject;
        this.tableModel    = new ProjectsTableModel();

        var pencilIcon = new FlatSVGIcon(ProjectsPanel.class.getResource("/icons/pencil.svg")).derive(12, 12);
        var table = new JTable(tableModel) {
            @Override
            public String getToolTipText(MouseEvent e) {
                var row = rowAtPoint(e.getPoint());
                var col = columnAtPoint(e.getPoint());
                if (row < 0 || col != 0) return null;
                var r = getCellRect(row, 0, false);
                if (e.getX() >= r.x + r.width - UiHelper.ACTION_PENCIL_W)
                    return "Open: " + tableModel.getRow(row).title();
                return null;
            }
        };
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);

        table.getColumnModel().getColumn(2).setCellRenderer(UiHelper.paperclipRenderer());
        table.getColumnModel().getColumn(2).setPreferredWidth(18);
        table.getColumnModel().getColumn(2).setMaxWidth(18);
        table.getColumn("Project").setCellRenderer(new TableCellRenderer() {
            private final JPanel cell   = new JPanel(new BorderLayout(4, 0));
            private final JLabel title  = new JLabel();
            private final JLabel pencil = new JLabel(pencilIcon);
            {
                cell.setOpaque(true);
                title.setOpaque(false);
                pencil.setOpaque(false);
                pencil.setPreferredSize(new Dimension(UiHelper.ACTION_PENCIL_W, 0));
                pencil.setHorizontalAlignment(SwingConstants.CENTER);
                cell.add(title,  BorderLayout.CENTER);
                cell.add(pencil, BorderLayout.EAST);
            }
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                cell.setBackground(isSelected ? t.getSelectionBackground() : t.getBackground());
                var archived = tableModel.getRow(row).status() == NodeStatus.ARCHIVED;
                title.setForeground(isSelected ? t.getSelectionForeground()
                        : archived ? Color.GRAY : t.getForeground());
                title.setText(archived ? value + "  (archived)" : (value != null ? value.toString() : ""));
                return cell;
            }
        });

        var projectEditor = new DefaultCellEditor(new JTextField());
        projectEditor.setClickCountToStart(99);
        table.getColumn("Project").setCellEditor(projectEditor);

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
                if (row < 0 || col != 0) return;
                var cellRect = table.getCellRect(row, 0, false);
                if (e.getX() >= cellRect.x + cellRect.width - UiHelper.ACTION_PENCIL_W) {
                    if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                    onOpenProject.accept(tableModel.getRow(row).id());
                } else if (e.getClickCount() == 2) {
                    if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                    onOpenProject.accept(tableModel.getRow(row).id());
                } else if (e.getClickCount() == 1 && row == lastRow[0] && AppSettings.getInstance().isClickToRename()) {
                    if (table.editCellAt(row, 0)) {
                        var ed = table.getEditorComponent();
                        if (ed instanceof JTextField tf) { tf.selectAll(); tf.requestFocusInWindow(); }
                    }
                }
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { popup(e); }
            @Override public void mouseReleased(MouseEvent e) { popup(e); }
            private void popup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                var row = table.rowAtPoint(e.getPoint());
                if (row < 0) return;
                table.setRowSelectionInterval(row, row);
                var item = tableModel.getRow(row);
                var menu = new JPopupMenu();
                var open = new JMenuItem("Open");
                open.addActionListener(a -> onOpenProject.accept(item.id()));
                menu.add(open);
                menu.addSeparator();
                if (item.status() == NodeStatus.ARCHIVED) {
                    var unarchive = new JMenuItem("Unarchive");
                    unarchive.addActionListener(a -> archive(item.id(), false));
                    menu.add(unarchive);
                } else {
                    var archive = new JMenuItem("Archive");
                    archive.addActionListener(a -> archive(item.id(), true));
                    menu.add(archive);
                }
                menu.show(table, e.getX(), e.getY());
            }
        });

        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "openProject");
        table.getActionMap().put("openProject", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent ev) {
                var row = table.getSelectedRow();
                if (row < 0) return;
                onOpenProject.accept(tableModel.getRow(row).id());
            }
        });

        var toolbar = new JToolBar();
        toolbar.setFloatable(false);
        var addButton = UiHelper.iconButton("Add project",
                new FlatSVGIcon(ProjectsPanel.class.getResource("/icons/folder-plus.svg")).derive(16, 16));
        addButton.setToolTipText("Add new top-level project");
        addButton.addActionListener(e -> addProject());
        toolbar.add(addButton);

        // View trio — List (compact table) / Columns / Readiness, over the top-level projects.
        listButton      = new JToggleButton(new FlatSVGIcon(ProjectsPanel.class.getResource("/icons/list-details.svg")).derive(16, 16));
        columnsButton   = new JToggleButton(new FlatSVGIcon(ProjectsPanel.class.getResource("/icons/layout-columns.svg")).derive(16, 16));
        readinessButton = new JToggleButton(new FlatSVGIcon(ProjectsPanel.class.getResource("/icons/layout-dashboard.svg")).derive(16, 16));
        listButton.setToolTipText("List view — the compact project list");
        columnsButton.setToolTipText("Column view — top-level projects as columns");
        readinessButton.setToolTipText("Readiness view — top-level project progress board");
        var viewGroup = new ButtonGroup();
        viewGroup.add(listButton);
        viewGroup.add(columnsButton);
        viewGroup.add(readinessButton);
        listButton.addActionListener(e -> setViewMode("LIST"));
        columnsButton.addActionListener(e -> setViewMode("COLUMNS"));
        readinessButton.addActionListener(e -> setViewMode("READINESS"));
        toolbar.addSeparator();
        toolbar.add(listButton);
        toolbar.add(columnsButton);
        toolbar.add(readinessButton);

        toolbar.addSeparator();
        var archivedButton = UiHelper.iconToggleButton("Archived",
                new FlatSVGIcon(ProjectsPanel.class.getResource("/icons/archive.svg")).derive(16, 16));
        archivedButton.setToolTipText("Show archived projects (right-click one to unarchive)");
        archivedButton.addActionListener(e -> { showArchived = archivedButton.isSelected(); refreshResults(); });
        toolbar.add(archivedButton);

        var settings = AppSettings.getInstance();
        viewMode = settings != null ? settings.getProjectsListView() : "LIST";

        matchLabel  = new JLabel("0 matches");
        clearButton = UiHelper.iconButton("Clear",
                new FlatSVGIcon(ProjectsPanel.class.getResource("/icons/eraser.svg")).derive(16, 16));
        clearButton.setToolTipText("Clear tag filter");
        clearButton.setEnabled(false);
        clearButton.addActionListener(e -> {
            tagBoxes.forEach(b -> b.setSelected(false));
            refreshResults();
        });

        filterPanel = new JPanel(new BorderLayout(0, 2));
        filterPanel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        var north = new JPanel(new BorderLayout());
        north.add(toolbar,     BorderLayout.NORTH);
        north.add(filterPanel, BorderLayout.CENTER);

        tableCard = UiHelper.tableCard(new JScrollPane(table), "No projects yet. Use + to add one.");
        center.add(tableCard, BorderLayout.CENTER);
        add(north,  BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
    }

    public void refresh() {
        rebuildFilterPanel();
        refreshResults();
        applyViewMode();
    }

    private void setViewMode(String mode) {
        viewMode = mode;
        var s = AppSettings.getInstance();
        if (s != null) {
            s.setProjectsListView(mode);
            try { s.save(); } catch (IOException ignored) {}
        }
        applyViewMode();
    }

    /** Shows the compact table or an embedded board for the current view mode. */
    private void applyViewMode() {
        listButton.setSelected("LIST".equals(viewMode));
        columnsButton.setSelected("COLUMNS".equals(viewMode));
        readinessButton.setSelected("READINESS".equals(viewMode));

        center.removeAll();
        if ("COLUMNS".equals(viewMode) || "READINESS".equals(viewMode)) {
            board = new ProjectWorkbenchPanel(SwingUtilities.getWindowAncestor(this),
                    workspace, service, workspace.getProjectsNodeId(), () -> {});
            board.configureAsBoard("COLUMNS".equals(viewMode), activeTags(), onOpenProject);
            center.add(board, BorderLayout.CENTER);
        } else {
            board = null;
            center.add(tableCard, BorderLayout.CENTER);
        }
        center.revalidate();
        center.repaint();
    }

    private java.util.Set<String> activeTags() {
        return tagBoxes.stream()
                .filter(JCheckBox::isSelected)
                .map(JCheckBox::getText)
                .collect(java.util.stream.Collectors.toSet());
    }

    private void rebuildFilterPanel() {
        var allTags = workspace.allTags();
        var selected = tagBoxes.stream()
                .filter(JCheckBox::isSelected)
                .map(JCheckBox::getText)
                .toList();
        tagBoxes.clear();
        filterPanel.removeAll();

        if (allTags.isEmpty()) {
            filterPanel.setVisible(false);
            return;
        }
        filterPanel.setVisible(true);

        var checkboxRow = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 2));
        for (var tag : allTags) {
            var box = new JCheckBox(tag, selected.contains(tag));
            box.setOpaque(false);
            box.addActionListener(e -> refreshResults());
            tagBoxes.add(box);
            checkboxRow.add(box);
        }

        var header = new JPanel(new BorderLayout());
        var headerLabel = new JLabel("Filter by tag");
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 8));
        headerLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.ITALIC, 11f));
        var clearWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        clearWrap.add(matchLabel);
        clearWrap.add(clearButton);
        header.add(headerLabel, BorderLayout.WEST);
        header.add(clearWrap,   BorderLayout.EAST);

        filterPanel.add(header,      BorderLayout.NORTH);
        filterPanel.add(checkboxRow, BorderLayout.CENTER);
        filterPanel.revalidate();
    }

    private void archive(UUID projectId, boolean archive) {
        if (!MonitoringModeGuard.checkAndConfirm(this)) return;
        try {
            if (archive) service.archiveProject(projectId);
            else         service.unarchiveProject(projectId);
            refresh();
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshResults() {
        var active = tagBoxes.stream()
                .filter(JCheckBox::isSelected)
                .map(JCheckBox::getText)
                .toList();
        clearButton.setEnabled(!active.isEmpty());
        var rows = new ProjectsLens().items(workspace, active, showArchived);
        tableModel.setRows(rows);
        if (active.isEmpty()) {
            matchLabel.setText("");
        } else {
            matchLabel.setText(rows.size() + (rows.size() == 1 ? " project" : " projects"));
        }
        UiHelper.setTableEmpty(tableCard, tableModel.getRowCount() == 0);
        if (board != null) board.setBoardTagFilter(activeTags());  // narrow the live board too
    }

    public void triggerAdd() { addProject(); }

    private void addProject() {
        var title = JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this), "Project title:", "Add project", JOptionPane.PLAIN_MESSAGE);
        if (title == null || title.isBlank()) return;
        try {
            service.addChild(workspace.getProjectsNodeId(), title.strip());
            refresh();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                    "Failed to save: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static final class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }
        @Override public Dimension preferredLayoutSize(Container t) { return layoutSize(t, true); }
        @Override public Dimension minimumLayoutSize(Container t)   { return layoutSize(t, false); }
        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                var w = target.getSize().width;
                if (w == 0) w = Integer.MAX_VALUE;
                var ins = target.getInsets();
                var maxW = w - ins.left - ins.right - getHgap() * 2;
                var dim = new Dimension(0, 0);
                int rowW = 0, rowH = 0;
                for (int i = 0; i < target.getComponentCount(); i++) {
                    var c = target.getComponent(i);
                    if (!c.isVisible()) continue;
                    var d = preferred ? c.getPreferredSize() : c.getMinimumSize();
                    if (rowW + d.width > maxW && rowW > 0) {
                        dim.width  = Math.max(dim.width, rowW);
                        dim.height += rowH + getVgap();
                        rowW = 0; rowH = 0;
                    }
                    rowW += d.width + getHgap();
                    rowH  = Math.max(rowH, d.height);
                }
                dim.width  = Math.max(dim.width, rowW);
                dim.height += rowH + getVgap() * 2 + ins.top + ins.bottom;
                return dim;
            }
        }
    }

    private final class ProjectsTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Project", "Tags", ""};
        private List<ProjectItemRow> rows = List.of();

        void setRows(List<ProjectItemRow> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        ProjectItemRow getRow(int index) { return rows.get(index); }

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
                ProjectsPanel.this.refresh();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        @Override
        public Class<?> getColumnClass(int col) { return col == 2 ? Boolean.class : String.class; }

        @Override
        public Object getValueAt(int row, int col) {
            var r = rows.get(row);
            return switch (col) {
                case 0 -> r.title();
                case 1 -> String.join(", ", r.tags());
                case 2 -> r.hasResources();
                default -> null;
            };
        }
    }
}
