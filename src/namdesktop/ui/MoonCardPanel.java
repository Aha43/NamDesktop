package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MoonCardPanel extends JPanel {

    public record Card(UUID id, String title, String description, String projectPath) {}

    private final List<Card>           cards;
    private final NamWorkspaceService  service;
    private final Runnable             onExit;
    private       int                  index = 0;

    private final JLabel    counterLabel;
    private final JLabel    titleLabel;
    private final JTextArea descArea;
    private final JLabel    pathLabel;
    private final JButton   prevButton;
    private final JButton   nextButton;
    private final JButton   doneButton;

    public MoonCardPanel(List<Card> cards, NamWorkspaceService service, Runnable onExit) {
        super(new BorderLayout(0, 0));
        this.cards   = new ArrayList<>(cards);
        this.service = service;
        this.onExit  = onExit;

        counterLabel = new JLabel("", SwingConstants.CENTER);
        counterLabel.setFont(counterLabel.getFont().deriveFont(Font.PLAIN, 12f));

        var exitButton = UiHelper.iconButton("Exit  [Esc]",
                new FlatSVGIcon(MoonCardPanel.class.getResource("/icons/logout.svg")).derive(16, 16));
        exitButton.setToolTipText("Exit Moon Cards (Esc)");
        exitButton.addActionListener(e -> onExit.run());

        var topBar = new JPanel(new BorderLayout());
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
        topBar.add(counterLabel, BorderLayout.CENTER);
        topBar.add(exitButton,   BorderLayout.EAST);

        titleLabel = new JLabel("", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));

        descArea = new JTextArea();
        descArea.setEditable(false);
        descArea.setWrapStyleWord(true);
        descArea.setLineWrap(true);
        descArea.setOpaque(false);
        descArea.setFont(descArea.getFont().deriveFont(14f));
        descArea.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0));
        descArea.setFocusable(false);

        pathLabel = new JLabel("", SwingConstants.CENTER);
        pathLabel.setFont(pathLabel.getFont().deriveFont(Font.ITALIC, 12f));
        pathLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        var cardContent = new JPanel(new BorderLayout(0, 4));
        cardContent.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(28, 36, 28, 36)));
        cardContent.add(titleLabel, BorderLayout.NORTH);
        cardContent.add(descArea,   BorderLayout.CENTER);
        cardContent.add(pathLabel,  BorderLayout.SOUTH);

        var centerWrapper = new JPanel(new GridBagLayout());
        var gbc = new GridBagConstraints();
        gbc.fill    = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.insets  = new Insets(32, 80, 32, 80);
        centerWrapper.add(cardContent, gbc);

        prevButton = new JButton("← Previous");
        doneButton = new JButton("Done  [Space]");
        nextButton = new JButton("Next →");
        prevButton.setToolTipText("Previous card (←)");
        doneButton.setToolTipText("Mark done and advance (Space)");
        nextButton.setToolTipText("Next card (→)");
        prevButton.addActionListener(e -> navigate(-1));
        nextButton.addActionListener(e -> navigate(1));
        doneButton.addActionListener(e -> markDoneAndAdvance());

        var footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        footer.add(prevButton);
        footer.add(doneButton);
        footer.add(nextButton);

        add(topBar,        BorderLayout.NORTH);
        add(centerWrapper, BorderLayout.CENTER);
        add(footer,        BorderLayout.SOUTH);

        keyDispatcher = e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED || !isShowing()) return false;
            return switch (e.getKeyCode()) {
                case KeyEvent.VK_LEFT   -> { navigate(-1); yield true; }
                case KeyEvent.VK_RIGHT  -> { navigate(1);  yield true; }
                case KeyEvent.VK_SPACE  -> { if (doneButton.isEnabled()) markDoneAndAdvance(); yield true; }
                case KeyEvent.VK_ESCAPE -> { onExit.run(); yield true; }
                default -> false;
            };
        };
        showCard();
    }

    private KeyEventDispatcher keyDispatcher;

    @Override
    public void addNotify() {
        super.addNotify();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher);
    }

    @Override
    public void removeNotify() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher);
        super.removeNotify();
    }

    private void showCard() {
        if (cards.isEmpty()) {
            counterLabel.setText("All done!");
            titleLabel.setText("No more actions");
            descArea.setText("");
            pathLabel.setText("");
            pathLabel.setVisible(false);
            prevButton.setEnabled(false);
            nextButton.setEnabled(false);
            doneButton.setEnabled(false);
            return;
        }
        var card = cards.get(index);
        counterLabel.setText((index + 1) + " / " + cards.size());
        titleLabel.setText(card.title());
        descArea.setText(card.description() != null ? card.description() : "");
        var path = card.projectPath();
        if (path != null && !path.isBlank()) {
            pathLabel.setText(path);
            pathLabel.setVisible(true);
        } else {
            pathLabel.setText("");
            pathLabel.setVisible(false);
        }
        prevButton.setEnabled(cards.size() > 1);
        nextButton.setEnabled(cards.size() > 1);
        doneButton.setEnabled(true);
    }

    private void navigate(int delta) {
        if (cards.isEmpty()) return;
        index = (index + delta + cards.size()) % cards.size();
        showCard();
    }

    private void markDoneAndAdvance() {
        if (cards.isEmpty()) return;
        var card = cards.get(index);
        try {
            service.markDone(card.id());
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        cards.remove(index);
        if (index >= cards.size()) index = Math.max(0, cards.size() - 1);
        showCard();
    }
}
