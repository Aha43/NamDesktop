package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.ui.UiHelper;
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
    private JButton addActionButton;
    private JButton saveViewButton;
    private JButton clearButton;

    public ContextPanel(NamWorkspace workspace, NamWorkspaceService service, Runnable onViewCreated) {
        super(new BorderLayout());
        this.workspace      = workspace;
        this.service        = service;
        this.onViewCreated  = onViewCreated;
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
        table.getColumn("Tags").setCellRenderer(UiHelper.tagsRenderer());
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
        var hasFilter = !selected.isEmpty();
        addActionButton.setEnabled(hasFilter);
        saveViewButton.setEnabled(hasFilter);
        clearButton.setEnabled(hasFilter);
    }

    private void addTaggedAction() {
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
