package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.ui.UiHelper;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static javax.swing.JOptionPane.*;

public final class ProjectDialog extends NodeDialog {

    private final UUID nodeId;
    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final ActionsTableModel tableModel;
    private final JTable actionsTable;

    public ProjectDialog(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service) {
        this(parent, nodeId, workspace, service, () -> {});
    }

    public ProjectDialog(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service, Runnable onChanged) {
        this(parent, nodeId, workspace, service, onChanged, null);
    }

    public ProjectDialog(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service, Runnable onChanged, UUID initialSelection) {
        super(parent, nodeId, workspace, service, onChanged);
        this.nodeId    = nodeId;
        this.workspace = workspace;
        this.service   = service;

        tableModel = new ActionsTableModel();
        actionsTable = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                var c = super.prepareRenderer(renderer, row, column);
                var status = tableModel.getRow(row).getStatus();
                c.setForeground(status == NodeStatus.DONE ? Color.GRAY : getForeground());
                return c;
            }
        };
        actionsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        actionsTable.setFillsViewportHeight(true);
        actionsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                var row = actionsTable.rowAtPoint(e.getPoint());
                if (row < 0) return;
                var childId = tableModel.getRow(row).getId();
                new ActionDialog(ProjectDialog.this, childId, workspace, service, false).setVisible(true);
                refreshChildList();
            }
        });

        var convertButton = new JButton("Convert to action");
        convertButton.addActionListener(e -> convertToAction());
        addToolbarButton(convertButton);

        var addActionButton = UiHelper.iconButton("Add action", new FlatSVGIcon(ProjectDialog.class.getResource("/icons/plus.svg")).derive(16, 16));
        addActionButton.addActionListener(e -> addAction());
        var actionsToolbar = new JToolBar();
        actionsToolbar.setFloatable(false);
        actionsToolbar.add(addActionButton);

        var actionsPanel = new JPanel(new BorderLayout());
        actionsPanel.setBorder(BorderFactory.createTitledBorder("Actions"));
        actionsPanel.setPreferredSize(new Dimension(0, 200));
        actionsPanel.add(actionsToolbar,                  BorderLayout.NORTH);
        actionsPanel.add(new JScrollPane(actionsTable),   BorderLayout.CENTER);

        addBelowDescription(actionsPanel);
        setSize(500, 580);

        refreshChildList();

        if (initialSelection != null) {
            selectRow(initialSelection);
        }
    }

    private void selectRow(UUID targetId) {
        var rows = tableModel.getRowCount();
        for (int i = 0; i < rows; i++) {
            if (tableModel.getRow(i).getId().equals(targetId)) {
                actionsTable.setRowSelectionInterval(i, i);
                actionsTable.scrollRectToVisible(actionsTable.getCellRect(i, 0, true));
                break;
            }
        }
    }

    private void convertToAction() {
        try {
            service.convertProjectToAction(nodeId);
            notifyChanged();
            dispose();
        } catch (IllegalStateException e) {
            showMessageDialog(this, "This project still has actions. Remove them before converting.",
                    "Cannot convert", ERROR_MESSAGE);
        } catch (IOException e) {
            showMessageDialog(this, "Failed to save: " + e.getMessage(), "Error", ERROR_MESSAGE);
        }
    }

    private void addAction() {
        var title = JOptionPane.showInputDialog(this, "Action title:", "Add action", JOptionPane.PLAIN_MESSAGE);
        if (title == null || title.isBlank()) return;
        try {
            service.addChild(nodeId, title.strip());
            refreshChildList();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshChildList() {
        tableModel.setRows(workspace.getChildren(nodeId));
    }

    private static final class ActionsTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Title", "Tags", "Status"};
        private List<NamNode> rows = List.of();

        void setRows(List<NamNode> rows) {
            this.rows = new ArrayList<>(rows);
            fireTableDataChanged();
        }

        NamNode getRow(int index) { return rows.get(index); }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            var n = rows.get(row);
            return switch (col) {
                case 0 -> n.getTitle();
                case 1 -> String.join(", ", n.getTags());
                case 2 -> n.getStatus();
                default -> null;
            };
        }
    }
}
