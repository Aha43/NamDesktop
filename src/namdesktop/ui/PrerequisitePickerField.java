package namdesktop.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class PrerequisitePickerField extends JTextField {

    record Candidate(UUID id, String title) {}

    private static final int MAX_VISIBLE_ROWS = 6;
    private static final int ROW_HEIGHT = 22;

    private final Supplier<List<Candidate>> candidateSource;
    private final Consumer<UUID> onSelected;
    private final DefaultListModel<Candidate> listModel = new DefaultListModel<>();
    private final JList<Candidate> suggestionList = new JList<>(listModel);
    private final JWindow popup;

    PrerequisitePickerField(Window owner, Supplier<List<Candidate>> candidateSource, Consumer<UUID> onSelected) {
        this.candidateSource = candidateSource;
        this.onSelected = onSelected;

        suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestionList.setFixedCellHeight(ROW_HEIGHT);
        suggestionList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Candidate c) setText(c.title());
                return this;
            }
        });

        popup = new JWindow(owner);
        popup.setFocusableWindowState(false);
        popup.add(new JScrollPane(suggestionList));

        suggestionList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { applySelection(); }
        });

        getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { SwingUtilities.invokeLater(PrerequisitePickerField.this::updatePopup); }
            @Override public void removeUpdate(DocumentEvent e)  { SwingUtilities.invokeLater(PrerequisitePickerField.this::updatePopup); }
            @Override public void changedUpdate(DocumentEvent e) {}
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!popup.isVisible()) return;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN -> {
                        suggestionList.setSelectedIndex(
                                Math.min(suggestionList.getSelectedIndex() + 1, listModel.size() - 1));
                        e.consume();
                    }
                    case KeyEvent.VK_UP -> {
                        suggestionList.setSelectedIndex(
                                Math.max(suggestionList.getSelectedIndex() - 1, 0));
                        e.consume();
                    }
                    case KeyEvent.VK_ENTER, KeyEvent.VK_TAB -> {
                        if (suggestionList.getSelectedIndex() >= 0) {
                            applySelection();
                            e.consume();
                        }
                    }
                    case KeyEvent.VK_ESCAPE -> hidePopup();
                }
            }
        });

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                var t = new Timer(150, ev -> hidePopup());
                t.setRepeats(false);
                t.start();
            }
        });
    }

    private void updatePopup() {
        var query = getText().strip().toLowerCase();
        if (query.isEmpty()) { hidePopup(); return; }

        var matches = candidateSource.get().stream()
                .filter(c -> c.title().toLowerCase().contains(query))
                .limit(MAX_VISIBLE_ROWS * 2L)
                .toList();

        if (matches.isEmpty()) { hidePopup(); return; }

        listModel.clear();
        matches.forEach(listModel::addElement);
        suggestionList.setSelectedIndex(0);

        var loc = getLocationOnScreen();
        var rows = Math.min(matches.size(), MAX_VISIBLE_ROWS);
        popup.setSize(getWidth(), rows * ROW_HEIGHT + 4);
        popup.setLocation(loc.x, loc.y + getHeight());
        popup.setVisible(true);
    }

    private void applySelection() {
        var selected = suggestionList.getSelectedValue();
        if (selected == null) return;
        hidePopup();
        setText("");
        onSelected.accept(selected.id());
    }

    private void hidePopup() { popup.setVisible(false); }
}
