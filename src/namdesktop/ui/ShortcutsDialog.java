package namdesktop.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;

public final class ShortcutsDialog extends JDialog {

    private static final boolean MAC =
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() == InputEvent.META_DOWN_MASK;

    private static String sc(String key)  { return MAC ? "⌘"  + key : "Ctrl+" + key; }
    private static String scs(String key) { return MAC ? "⌘⇧" + key : "Ctrl+Shift+" + key; }

    private ShortcutsDialog(Window parent) {
        super(parent, "Keyboard Shortcuts", ModalityType.APPLICATION_MODAL);

        var content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(8, 16, 12, 16));

        section(content, "Navigation",
                sc("1"),    "Inbox",
                sc("2"),    "Next Actions",
                sc("3"),    "Backlog",
                sc("4"),    "Projects",
                sc("5"),    "Done");

        section(content, "Capture & Search",
                sc("I"),    "Capture to Inbox",
                sc("F"),    "Open Search");

        section(content, "Monitoring",
                scs("M"),   "Toggle Monitoring Mode",
                scs("S"),   "Checkpoint — flush changes, stay in monitoring");

        section(content, "View",
                scs("T"),   "Show / Hide Toolbar",
                scs("N"),   "Show / Hide Nav Pane",
                scs("Z"),   "Enter / Exit Zen Mode");

        section(content, "General",
                sc(","),    "Settings",
                sc("Q"),    "Quit",
                "F1",       "Help",
                sc("/"),    "Keyboard Shortcuts");

        var scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        var closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(closeButton);

        var footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(closeButton);

        setLayout(new BorderLayout());
        add(scroll, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        setSize(460, 430);
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    private static void section(JPanel parent, String title, String... pairs) {
        var header = new JLabel(title);
        header.setFont(header.getFont().deriveFont(Font.BOLD, header.getFont().getSize() + 1f));
        header.setBorder(BorderFactory.createEmptyBorder(12, 0, 4, 0));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(header);

        var sep = new JSeparator();
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        parent.add(sep);

        var grid = new JPanel(new GridBagLayout());
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 0, 2, 12);
        gbc.anchor = GridBagConstraints.WEST;

        for (int i = 0; i < pairs.length; i += 2) {
            gbc.gridx = 0; gbc.gridy = i / 2;
            gbc.fill  = GridBagConstraints.NONE;
            gbc.weightx = 0;
            var keyLabel = new JLabel(pairs[i]);
            keyLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            keyLabel.setOpaque(true);
            keyLabel.setBackground(UIManager.getColor("TextField.background"));
            keyLabel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")),
                    BorderFactory.createEmptyBorder(1, 5, 1, 5)));
            grid.add(keyLabel, gbc);

            gbc.gridx = 1;
            gbc.fill  = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            gbc.insets = new Insets(2, 0, 2, 0);
            grid.add(new JLabel(pairs[i + 1]), gbc);
            gbc.insets = new Insets(2, 0, 2, 12);
        }

        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
        parent.add(grid);
    }

    public static void show(Window parent) {
        new ShortcutsDialog(parent).setVisible(true);
    }
}
