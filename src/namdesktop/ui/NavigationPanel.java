package namdesktop.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public final class NavigationPanel extends JPanel {

    public NavigationPanel(List<NavigationEntry> entries, Consumer<NavigationEntry> onSelect) {
        setLayout(new BorderLayout());

        var model = new DefaultListModel<NavigationEntry>();
        entries.forEach(model::addElement);

        var list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new EntryRenderer());
        list.setBorder(new EmptyBorder(4, 4, 4, 4));

        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                var selected = list.getSelectedValue();
                if (selected != null) onSelect.accept(selected);
            }
        });

        add(new JScrollPane(list), BorderLayout.CENTER);

        if (!entries.isEmpty()) list.setSelectedIndex(0);
    }

    private static final class EntryRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof NavigationEntry entry) setText(entry.title());
            return this;
        }
    }
}
