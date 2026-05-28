package namdesktop.ui;

import javax.swing.*;
import java.awt.*;

public final class ProcessInboxDialog extends JDialog {

    public enum Result { NEXT_ACTION, PARK_FOR_LATER, PROJECT, CANCELLED }

    private Result result = Result.CANCELLED;

    private ProcessInboxDialog(Window owner, String itemTitle) {
        super(owner, "Process: “" + truncate(itemTitle, 48) + "”", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        var cards = new JPanel(new CardLayout());

        // ── Step 1 ────────────────────────────────────────────────────────
        var step1 = new JPanel(new BorderLayout(0, 12));
        step1.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        var prompt1 = new JLabel("What is this?");
        prompt1.setFont(prompt1.getFont().deriveFont(Font.BOLD, 14f));
        step1.add(prompt1, BorderLayout.NORTH);

        var actionBtn = new JButton("<html><b>It’s one action</b><br><small>Something I can do directly</small></html>");
        var projectBtn = new JButton("<html><b>It needs planning</b><br><small>More than one step required</small></html>");
        for (var btn : new JButton[]{actionBtn, projectBtn}) {
            btn.setHorizontalAlignment(SwingConstants.LEFT);
            btn.setMargin(new Insets(10, 14, 10, 14));
        }

        var step1Buttons = new JPanel(new GridLayout(2, 1, 0, 8));
        step1Buttons.add(actionBtn);
        step1Buttons.add(projectBtn);
        step1.add(step1Buttons, BorderLayout.CENTER);

        // ── Step 2 (action path) ──────────────────────────────────────────
        var step2 = new JPanel(new BorderLayout(0, 12));
        step2.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        var prompt2 = new JLabel("When should it happen?");
        prompt2.setFont(prompt2.getFont().deriveFont(Font.BOLD, 14f));
        step2.add(prompt2, BorderLayout.NORTH);

        var nextBtn = new JButton("<html><b>Do it next</b><br><small>Moves to Next Actions</small></html>");
        var parkBtn = new JButton("<html><b>Park for later</b><br><small>Moves to Backlog — not urgent yet</small></html>");
        for (var btn : new JButton[]{nextBtn, parkBtn}) {
            btn.setHorizontalAlignment(SwingConstants.LEFT);
            btn.setMargin(new Insets(10, 14, 10, 14));
        }
        var backBtn = new JButton("← Back");

        var step2Top = new JPanel(new GridLayout(2, 1, 0, 8));
        step2Top.add(nextBtn);
        step2Top.add(parkBtn);
        var step2Bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        step2Bottom.add(backBtn);

        step2.add(step2Top,    BorderLayout.CENTER);
        step2.add(step2Bottom, BorderLayout.SOUTH);

        cards.add(step1, "step1");
        cards.add(step2, "step2");
        getContentPane().add(cards);

        // ── Wiring ────────────────────────────────────────────────────────
        var cl = (CardLayout) cards.getLayout();

        actionBtn.addActionListener(e -> cl.show(cards, "step2"));
        projectBtn.addActionListener(e -> { result = Result.PROJECT;      dispose(); });
        backBtn.addActionListener(e    -> cl.show(cards, "step1"));
        nextBtn.addActionListener(e    -> { result = Result.NEXT_ACTION;  dispose(); });
        parkBtn.addActionListener(e    -> { result = Result.PARK_FOR_LATER; dispose(); });

        pack();
        setMinimumSize(new Dimension(360, getHeight()));
        setLocationRelativeTo(owner);
    }

    public static Result show(Window owner, String itemTitle) {
        var d = new ProcessInboxDialog(owner, itemTitle);
        d.setVisible(true);
        return d.result;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
