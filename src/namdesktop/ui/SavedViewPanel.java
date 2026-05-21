package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.ui.UiHelper;
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

public final class SavedViewPanel extends JPanel {

    private final SavedView view;
    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final Runnable onDeleted;
    private final ViewTableModel tableModel;

    public SavedViewPanel(SavedView view, NamWorkspace workspace, NamWorkspaceService service, Runnable onDeleted) {
        super(new BorderLayout());
        this.view       = view;
        this.workspace  = workspace;
        this.service    = service;
        this.onDeleted  = onDeleted;
        this.tableModel = new ViewTableModel();

        var nameLabel = new JLabel(view.name());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));

        var addActionButton = UiHelper.iconButton("Add action", new FlatSVGIcon(SavedViewPanel.class.getResource("/icons/plus.svg")).derive(16, 16));
        addActionButton.addActionListener(e -> addTaggedAction());

        var deleteButton = new JButton("Delete view");
        deleteButton.addActionListener(e -> deleteView());

        var eastButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        eastButtons.add(addActionButton);
        eastButtons.add(deleteButton);

        var header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(4, 6, 2, 6));
        header.add(nameLabel,   BorderLayout.WEST);
        header.add(eastButtons, BorderLayout.EAST);

        var tagsLabel = new JLabel("Tags: " + String.join(", ", view.tags()));
        tagsLabel.setBorder(BorderFactory.createEmptyBorder(0, 6, 4, 6));

        var northPanel = new JPanel(new BorderLayout());
        northPanel.add(header,    BorderLayout.NORTH);
        northPanel.add(tagsLabel, BorderLayout.CENTER);

        JTable table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                var c = super.prepareRenderer(renderer, row, column);
                c.setForeground(tableModel.getRow(row).status() == NodeStatus.DONE
                        ? Color.GRAY : getForeground());
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
                new ActionDialog(SwingUtilities.getWindowAncestor(SavedViewPanel.this),
                        tableModel.getRow(row).id(), workspace, service, true,
                        SavedViewPanel.this::refresh).setVisible(true);
            }
        });

        add(northPanel,             BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        refresh();
    }

    public void refresh() {
        tableModel.setRows(new ContextLens().items(workspace, view.tags()));
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

    private void deleteView() {
        var confirm = JOptionPane.showConfirmDialog(this,
                "Delete saved view \"" + view.name() + "\"?",
                "Delete view", JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) return;
        try {
            service.deleteSavedView(view.name());
            onDeleted.run();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static final class ViewTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Title", "Project", "Tags"};
        private List<ContextItemRow> rows = List.of();

        void setRows(List<ContextItemRow> rows) { this.rows = rows; fireTableDataChanged(); }
        ContextItemRow getRow(int i) { return rows.get(i); }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            var r = rows.get(row);
            return switch (col) {
                case 0 -> r.title();
                case 1 -> r.parentTitle() != null ? r.parentTitle() : "";
                case 2 -> String.join(", ", r.tags());
                default -> null;
            };
        }
    }
}
