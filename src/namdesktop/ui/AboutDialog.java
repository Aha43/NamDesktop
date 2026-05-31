package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.app.AppInfo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;

public final class AboutDialog extends JDialog {

    public AboutDialog(Window owner) {
        super(owner, "About " + AppInfo.NAME, ModalityType.APPLICATION_MODAL);

        var wordmarkIcon  = new FlatSVGIcon(AboutDialog.class.getResource("/icons/logo-wordmark.svg")).derive(200, 60);
        var wordmarkLabel = new JLabel(wordmarkIcon);
        wordmarkLabel.setHorizontalAlignment(SwingConstants.CENTER);

        var versionLabel = new JLabel("Version " + AppInfo.version());
        versionLabel.setFont(versionLabel.getFont().deriveFont(12f));
        versionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        versionLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        var content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(32, 48, 24, 48));
        wordmarkLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(wordmarkLabel);
        content.add(Box.createVerticalStrut(6));
        content.add(versionLabel);
        content.add(Box.createVerticalStrut(24));

        var repoLabel = new JLabel("<html><a href=''>GitHub repository</a></html>");
        repoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        repoLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        repoLabel.setToolTipText(AppInfo.REPO_URL);
        repoLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                try { Desktop.getDesktop().browse(new URI(AppInfo.REPO_URL)); } catch (Exception ignored) {}
            }
        });
        content.add(repoLabel);
        content.add(Box.createVerticalStrut(4));

        var repoUrlField = new JTextField(AppInfo.REPO_URL);
        repoUrlField.setEditable(false);
        repoUrlField.setFont(repoUrlField.getFont().deriveFont(10f));
        repoUrlField.setForeground(UIManager.getColor("Label.disabledForeground"));
        repoUrlField.setBorder(null);
        repoUrlField.setBackground(UIManager.getColor("Panel.background"));
        repoUrlField.setHorizontalAlignment(JTextField.CENTER);
        repoUrlField.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(repoUrlField);
        content.add(Box.createVerticalStrut(16));

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
