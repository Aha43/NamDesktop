package namdesktop.ui;

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
import java.util.stream.Collectors;

public final class ContextPanel extends JPanel {

    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final Runnable onViewCreated;
    private final ContextTableModel tableModel;
    private final JPanel tagSelectorPanel;
    private final List<JCheckBox> tagBoxes = new ArrayList<>();
    private JLabel matchLabel;
    private JButton saveViewButton;

    public ContextPanel(NamWorkspace workspace, NamWorkspaceService service, Runnable onViewCreated) {
        super(new BorderLayout());
        this.workspace      = workspace;
        this.service        = service;
        this.onViewCreated  = onViewCreated;
        this.tableModel     = new ContextTableModel();

        matchLabel = new JLabel("0 matches");

        var clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> {
            tagBoxes.forEach(b -> b.setSelected(false));
            refreshResults();
        });

        saveViewButton = new JButton("Save as view…");
        saveViewButton.setEnabled(false);
        saveViewButton.addActionListener(e -> saveCurrentView());

        var eastButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        eastButtons.add(saveViewButton);
        eastButtons.add(clearButton);

        var selectorHeader = new JPanel(new BorderLayout());
        selectorHeader.add(matchLabel,  BorderLayout.WEST);
        selectorHeader.add(eastButtons, BorderLayout.EAST);

        tagSelectorPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));
        tagSelectorPanel.setBorder(BorderFactory.createTitledBorder("Filter by tags (AND)"));

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
                var selected = tableModel.getRow(row);
                new ActionDialog(SwingUtilities.getWindowAncestor(ContextPanel.this),
                        selected.id(), workspace, service, true, ContextPanel.this::refreshResults).setVisible(true);
            }
        });

        var northPanel = new JPanel(new BorderLayout());
        northPanel.add(selectorHeader,  BorderLayout.NORTH);
        northPanel.add(tagSelectorPanel, BorderLayout.CENTER);

        add(northPanel,               BorderLayout.NORTH);
        add(new JScrollPane(table),   BorderLayout.CENTER);
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
        matchLabel.setText(rows.size() + (rows.size() == 1 ? " match" : " matches"));
        saveViewButton.setEnabled(!selected.isEmpty());
    }

    private void saveCurrentView() {
        var selected = tagBoxes.stream()
                .filter(JCheckBox::isSelected)
                .map(JCheckBox::getText)
                .toList();
        var name = JOptionPane.showInputDialog(this, "View name:", "Save as view", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return;
        try {
            service.createSavedView(name, selected);
            onViewCreated.run();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot save view", JOptionPane.ERROR_MESSAGE);
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static final class ContextTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Title", "Project", "Tags"};
        private List<ContextItemRow> rows = List.of();

        void setRows(List<ContextItemRow> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        ContextItemRow getRow(int index) { return rows.get(index); }

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
