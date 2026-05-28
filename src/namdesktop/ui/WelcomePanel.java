package namdesktop.ui;

import javax.swing.*;
import java.awt.*;

final class WelcomePanel extends JPanel {

    WelcomePanel(Runnable onDemo, Runnable onFresh) {
        super(new GridBagLayout());

        var inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.PAGE_AXIS));
        inner.setOpaque(false);

        var heading = new JLabel("Welcome to NamDesktop");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 22f));
        heading.setAlignmentX(CENTER_ALIGNMENT);

        var tagline = new JLabel("<html><div style='text-align:center'>"
                + "Capture everything on your mind, decide what to do with it,<br>"
                + "and keep a clear list of what's next."
                + "</div></html>");
        tagline.setFont(tagline.getFont().deriveFont(14f));
        tagline.setAlignmentX(CENTER_ALIGNMENT);
        tagline.setForeground(UIManager.getColor("Label.disabledForeground"));

        inner.add(heading);
        inner.add(Box.createVerticalStrut(10));
        inner.add(tagline);
        inner.add(Box.createVerticalStrut(32));

        var choiceRow = new JPanel(new GridLayout(1, 2, 20, 0));
        choiceRow.setOpaque(false);
        choiceRow.setMaximumSize(new Dimension(560, 140));
        choiceRow.setAlignmentX(CENTER_ALIGNMENT);
        choiceRow.add(choiceCard(
                "Explore demo workspace",
                "See a pre-filled example to learn how NamDesktop works.",
                onDemo));
        choiceRow.add(choiceCard(
                "Start fresh",
                "Begin with an empty workspace. Your Inbox is waiting.",
                onFresh));

        inner.add(choiceRow);

        add(inner);
    }

    private JPanel choiceCard(String title, String description, Runnable onClick) {
        var card = new JPanel(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        var titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));

        var descLabel = new JLabel("<html><div style='width:180px'>" + description + "</div></html>");
        descLabel.setFont(descLabel.getFont().deriveFont(12f));
        descLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(descLabel,  BorderLayout.CENTER);

        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { onClick.run(); }
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(UIManager.getColor("Component.focusColor"), 2, true),
                        BorderFactory.createEmptyBorder(15, 17, 15, 17)));
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true),
                        BorderFactory.createEmptyBorder(16, 18, 16, 18)));
            }
        });
        return card;
    }
}
