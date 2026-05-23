package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
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
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class ProjectsPanel extends JPanel {

    private final NamWorkspace      workspace;
    private final NamWorkspaceService service;
    private final Consumer<UUID>    onOpenProject;
    private final ProjectsTableModel tableModel;

    public ProjectsPanel(NamWorkspace workspace, NamWorkspaceService service, Consumer<UUID> onOpenProject) {
        super(new BorderLayout());
        this.workspace     = workspace;
        this.service       = service;
        this.onOpenProject = onOpenProject;
        this.tableModel    = new ProjectsTableModel();

        JTable table = new JTable(tableModel) {
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
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                var row = table.rowAtPoint(e.getPoint());
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

        add(toolbar,               BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void refresh() {
        tableModel.setRows(new ProjectsLens().items(workspace));
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

    private static final class ProjectsTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Title", "Tags", "Status"};
        private List<ProjectItemRow> rows = List.of();

        void setRows(List<ProjectItemRow> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        ProjectItemRow getRow(int index) { return rows.get(index); }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            var r = rows.get(row);
            return switch (col) {
                case 0 -> r.title();
                case 1 -> String.join(", ", r.tags());
                case 2 -> r.status();
                default -> null;
            };
        }
    }
}
