package namdesktop.ui;

import namdesktop.model.SavedView;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public final class NavigationPanel extends JPanel {

    private final List<NavigationEntry> staticEntries;
    private final DefaultListModel<NavigationEntry> model;
    private final JList<NavigationEntry> list;

    public NavigationPanel(List<NavigationEntry> staticEntries, Consumer<NavigationEntry> onSelect) {
        setLayout(new BorderLayout());
        this.staticEntries = List.copyOf(staticEntries);
        this.model = new DefaultListModel<>();
        staticEntries.forEach(model::addElement);

        list = new JList<>(model) {
            @Override
            public String getToolTipText(java.awt.event.MouseEvent e) {
                int idx = locationToIndex(e.getPoint());
                if (idx < 0) return null;
                return model.get(idx).tooltip();
            }
        };
        list.setToolTipText("");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new EntryRenderer());
        list.setBorder(new EmptyBorder(4, 4, 4, 4));

        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            var selected = list.getSelectedValue();
            if (selected == null) return;
            if (selected.isDivider() || selected.isSectionHeader()) { list.clearSelection(); return; }
            onSelect.accept(selected);
        });

        add(new JScrollPane(list), BorderLayout.CENTER);

        if (!staticEntries.isEmpty()) list.setSelectedIndex(0);
    }

    public void rebuildSavedViews(List<SavedView> savedViews) {
        while (model.size() > staticEntries.size()) model.removeElementAt(model.size() - 1);
        if (!savedViews.isEmpty()) {
            model.addElement(NavigationEntry.sectionHeader("Saved Views"));
            savedViews.forEach(sv -> {
                var tooltip = sv.tags().isEmpty()
                        ? "No tag filter"
                        : "Tags: " + String.join(", ", sv.tags());
                if (sv.nextOnly()) tooltip += " · Next only";
                model.addElement(new NavigationEntry("saved-view:" + sv.name(), sv.name(), tooltip));
            });
        }
    }

    public void selectById(String id) {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).id().equals(id)) { list.setSelectedIndex(i); return; }
        }
    }

    private static final class EntryRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            if (value instanceof NavigationEntry e && e.isDivider()) {
                var sep = new JSeparator();
                sep.setBorder(new EmptyBorder(3, 4, 3, 4));
                return sep;
            }
            if (value instanceof NavigationEntry e && e.isSectionHeader()) {
                var label = new JLabel(e.title().toUpperCase());
                label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
                label.setForeground(UIManager.getColor("Label.disabledForeground"));
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                        new EmptyBorder(6, 6, 2, 4)));
                return label;
            }
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof NavigationEntry e) setText(e.title());
            // always use the focused selection colour so it doesn't shift when focus moves to the workbench
            if (isSelected) {
                setBackground(UIManager.getColor("List.selectionBackground"));
                setForeground(UIManager.getColor("List.selectionForeground"));
            }
            return this;
        }
    }
}
