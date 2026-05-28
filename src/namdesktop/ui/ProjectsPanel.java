package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.lens.ProjectItemRow;
import namdesktop.lens.ProjectsLens;
import namdesktop.model.NamWorkspace;
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

public final class ProjectsPanel extends JPanel {

    private final NamWorkspace      workspace;
    private final NamWorkspaceService service;
    private final Consumer<UUID>    onOpenProject;
    private final ProjectsTableModel tableModel;
    private JPanel tableCard;

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
                title.setForeground(isSelected ? t.getSelectionForeground() : t.getForeground());
                title.setText(value != null ? value.toString() : "");
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
                } else if (e.getClickCount() == 1 && row == lastRow[0]) {
                    if (table.editCellAt(row, 0)) {
                        var ed = table.getEditorComponent();
                        if (ed instanceof JTextField tf) { tf.selectAll(); tf.requestFocusInWindow(); }
                    }
                }
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

        tableCard = UiHelper.tableCard(new JScrollPane(table), "No projects yet. Use + to add one.");
        add(toolbar,    BorderLayout.NORTH);
        add(tableCard,  BorderLayout.CENTER);
    }

    public void refresh() {
        tableModel.setRows(new ProjectsLens().items(workspace));
        UiHelper.setTableEmpty(tableCard, tableModel.getRowCount() == 0);
    }

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

    private final class ProjectsTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Project", "Tags"};
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
        public Object getValueAt(int row, int col) {
            var r = rows.get(row);
            return switch (col) {
                case 0 -> r.title();
                case 1 -> String.join(", ", r.tags());
                default -> null;
            };
        }
    }
}
