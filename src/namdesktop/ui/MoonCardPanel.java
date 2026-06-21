package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.model.NodeStatus;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class MoonCardPanel extends JPanel {

    /** {@code projectSegments} is the clickable breadcrumb to the action's project (#382), root→leaf. */
    public record Card(UUID id, String title, String description,
                       List<ProjectPathSupport.Segment> projectSegments) {}

    private final List<Card>           cards;
    private final NamWorkspaceService  service;
    private final Runnable             onExit;
    private final Consumer<UUID>       onOpenProject;
    private final NodeStatus           flipTarget; // re-triage target (#400); null = no flip button
    private       int                  index = 0;

    private final JLabel    counterLabel;
    private final JLabel    titleLabel;
    private final JTextArea descArea;
    private final JPanel    pathPanel;
    private final JButton   prevButton;
    private final JButton   nextButton;
    private final JButton   doneButton;
    private final JButton   flipButton; // null when no re-triage target

    public MoonCardPanel(List<Card> cards, NamWorkspaceService service, Runnable onExit,
                         Consumer<UUID> onOpenProject) {
        this(cards, service, onExit, onOpenProject, null, null);
    }

    /**
     * @param flipTarget optional re-triage status (#400) — when set with {@code flipLabel}, a secondary
     *        "Move to {label}" button flips the card's status and advances, like Done. Pass null for
     *        status-mixed decks (project-scoped / saved-view focus) where the target is ambiguous.
     */
    public MoonCardPanel(List<Card> cards, NamWorkspaceService service, Runnable onExit,
                         Consumer<UUID> onOpenProject, NodeStatus flipTarget, String flipLabel) {
        super(new BorderLayout(0, 0));
        this.cards         = new ArrayList<>(cards);
        this.service       = service;
        this.onExit        = onExit;
        this.onOpenProject = onOpenProject != null ? onOpenProject : id -> {};
        this.flipTarget    = flipTarget;

        counterLabel = new JLabel("", SwingConstants.CENTER);
        counterLabel.setFont(counterLabel.getFont().deriveFont(Font.PLAIN, 12f));

        var exitButton = UiHelper.iconButton("Exit  [Esc]",
                new FlatSVGIcon(MoonCardPanel.class.getResource("/icons/logout.svg")).derive(16, 16));
        exitButton.setToolTipText("Exit focus mode (Esc)");
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

        pathPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        pathPanel.setOpaque(false);

        var cardContent = new JPanel(new BorderLayout(0, 4));
        cardContent.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(28, 36, 28, 36)));
        cardContent.add(titleLabel, BorderLayout.NORTH);
        cardContent.add(descArea,   BorderLayout.CENTER);
        cardContent.add(pathPanel,  BorderLayout.SOUTH);

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

        if (flipTarget != null && flipLabel != null) {
            flipButton = new JButton("Move to " + flipLabel);
            flipButton.setToolTipText("Move this card to " + flipLabel + " and advance");
            flipButton.addActionListener(e -> flipAndAdvance());
        } else {
            flipButton = null;
        }

        var footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        footer.add(prevButton);
        footer.add(doneButton);
        if (flipButton != null) footer.add(flipButton);
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
            counterLabel.setText("0 / 0");
            titleLabel.setText("All done!");
            descArea.setText("");
            setPath(List.of());
            prevButton.setEnabled(false);
            nextButton.setEnabled(false);
            doneButton.setEnabled(false);
            if (flipButton != null) flipButton.setEnabled(false);
            return;
        }
        var card = cards.get(index);
        counterLabel.setText((index + 1) + " / " + cards.size());
        titleLabel.setText(card.title());
        descArea.setText(card.description() != null ? card.description() : "");
        setPath(card.projectSegments());
        prevButton.setEnabled(cards.size() > 1);
        nextButton.setEnabled(cards.size() > 1);
        doneButton.setEnabled(true);
        if (flipButton != null) flipButton.setEnabled(true);
    }

    /** Rebuilds the breadcrumb as clickable segment links separated by {@code " > "}. */
    private void setPath(List<ProjectPathSupport.Segment> segs) {
        pathPanel.removeAll();
        if (segs == null || segs.isEmpty()) {
            pathPanel.setVisible(false);
        } else {
            pathPanel.setVisible(true);
            for (int i = 0; i < segs.size(); i++) {
                if (i > 0) pathPanel.add(separatorLabel());
                pathPanel.add(linkLabel(segs.get(i)));
            }
        }
        pathPanel.revalidate();
        pathPanel.repaint();
    }

    private JLabel linkLabel(ProjectPathSupport.Segment seg) {
        var lbl = new JLabel(seg.title());
        lbl.setFont(lbl.getFont().deriveFont(Font.ITALIC, 12f));
        lbl.setForeground(ProjectPathSupport.linkColor());
        lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lbl.setToolTipText("Open project: " + seg.title());
        lbl.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { onOpenProject.accept(seg.id()); }
        });
        return lbl;
    }

    private JLabel separatorLabel() {
        var sep = new JLabel(ProjectPathSupport.SEPARATOR);
        sep.setFont(sep.getFont().deriveFont(Font.ITALIC, 12f));
        sep.setForeground(UIManager.getColor("Label.disabledForeground"));
        return sep;
    }

    private void navigate(int delta) {
        if (cards.isEmpty()) return;
        index = (index + delta + cards.size()) % cards.size();
        showCard();
    }

    private void markDoneAndAdvance() {
        if (cards.isEmpty()) return;
        if (!MonitoringModeGuard.checkAndConfirm(this)) return;
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

    /** Re-triage the current card to {@link #flipTarget} and advance, mirroring {@link #markDoneAndAdvance}. */
    private void flipAndAdvance() {
        if (cards.isEmpty() || flipTarget == null) return;
        if (!MonitoringModeGuard.checkAndConfirm(this)) return;
        var card = cards.get(index);
        try {
            switch (flipTarget) {
                case NEXT    -> service.markNext(card.id());
                case BACKLOG -> service.markBacklog(card.id());
                default      -> { return; }
            }
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        cards.remove(index);
        if (index >= cards.size()) index = Math.max(0, cards.size() - 1);
        showCard();
    }
}
