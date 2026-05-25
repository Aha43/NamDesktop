package namdesktop.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Supplier;

final class TagsField extends JTextField {

    private static final int MAX_VISIBLE_ROWS = 6;
    private static final int ROW_HEIGHT = 22;

    private final Supplier<List<String>> tagSource;
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> suggestionList = new JList<>(listModel);
    private final JWindow popup;

    TagsField(Window owner, Supplier<List<String>> tagSource) {
        this.tagSource = tagSource;

        suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestionList.setFixedCellHeight(ROW_HEIGHT);

        popup = new JWindow(owner);
        popup.setFocusableWindowState(false);
        popup.add(new JScrollPane(suggestionList));

        suggestionList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { applySelection(); }
        });

        getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { SwingUtilities.invokeLater(TagsField.this::updatePopup); }
            @Override public void removeUpdate(DocumentEvent e)  { SwingUtilities.invokeLater(TagsField.this::updatePopup); }
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
        var token = currentToken();
        if (token.isEmpty()) { hidePopup(); return; }

        var alreadyPresent = parsedTags();
        var matches = tagSource.get().stream()
                .filter(t -> t.contains(token) && !alreadyPresent.contains(t))
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
        var text = getText();
        var lastComma = text.lastIndexOf(',');
        var prefix = lastComma >= 0 ? text.substring(0, lastComma + 1) + " " : "";
        setText(prefix + selected + ", ");
        setCaretPosition(getText().length());
    }

    private void hidePopup() { popup.setVisible(false); }

    private String currentToken() {
        var text = getText();
        var lastComma = text.lastIndexOf(',');
        return (lastComma >= 0 ? text.substring(lastComma + 1) : text).strip().toLowerCase();
    }

    List<String> getTags() { return parsedTags(); }

    private List<String> parsedTags() {
        var result = new java.util.ArrayList<String>();
        for (var part : getText().split(",")) {
            var t = part.strip().toLowerCase();
            if (!t.isEmpty()) result.add(t);
        }
        return result;
    }
}
