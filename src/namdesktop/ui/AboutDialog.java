package namdesktop.ui;

import namdesktop.app.AppInfo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class AboutDialog extends JDialog {

    public AboutDialog(Window owner) {
        super(owner, "About " + AppInfo.NAME, ModalityType.APPLICATION_MODAL);

        var nameLabel = new JLabel(AppInfo.NAME);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 18f));
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);

        var versionLabel = new JLabel("Version " + AppInfo.version());
        versionLabel.setFont(versionLabel.getFont().deriveFont(12f));
        versionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        versionLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        var content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(32, 48, 24, 48));
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(nameLabel);
        content.add(Box.createVerticalStrut(6));
        content.add(versionLabel);
        content.add(Box.createVerticalStrut(24));

        var closeButton = new JButton("Close");
        closeButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        closeButton.addActionListener(e -> dispose());
        content.add(closeButton);

        setContentPane(content);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }
}
