package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.app.AppInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SplashDialog extends JDialog {

    private static final Path DEV_MODE_FLAG = Path.of(
            System.getProperty("user.home"), ".namdesktop", ".devmode");

    private final JCheckBox devModeBox;

    public SplashDialog() {
        super((Frame) null, AppInfo.NAME, true);

        devModeBox = new JCheckBox("Run in dev mode", Files.exists(DEV_MODE_FLAG));

        var logoIcon  = new FlatSVGIcon(SplashDialog.class.getResource("/icons/logo-tile.svg")).derive(96, 96);
        var logoLabel = new JLabel(logoIcon);
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);

        var titleLabel = new JLabel(AppInfo.NAME + " " + AppInfo.version());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        var launchButton = new JButton("Launch");
        launchButton.addActionListener(e -> {
            persistChoice();
            dispose();
        });
        getRootPane().setDefaultButton(launchButton);

        var devModePanel = new JPanel(new BorderLayout());
        devModePanel.setBorder(BorderFactory.createEmptyBorder(12, 8, 8, 8));
        devModePanel.add(devModeBox, BorderLayout.NORTH);

        var footer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        footer.add(launchButton);

        var header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(logoLabel);
        header.add(Box.createVerticalStrut(8));
        header.add(titleLabel);

        var content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(24, 40, 20, 40));
        content.add(header,       BorderLayout.NORTH);
        content.add(devModePanel, BorderLayout.CENTER);
        content.add(footer,       BorderLayout.SOUTH);

        setContentPane(content);
        setResizable(false);
        pack();
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { System.exit(0); }
        });
    }

    public boolean isDevMode() { return devModeBox.isSelected(); }

    private void persistChoice() {
        try {
            Files.createDirectories(DEV_MODE_FLAG.getParent());
            if (devModeBox.isSelected()) {
                if (!Files.exists(DEV_MODE_FLAG)) Files.createFile(DEV_MODE_FLAG);
            } else {
                Files.deleteIfExists(DEV_MODE_FLAG);
            }
        } catch (IOException e) {
            System.err.println("Could not persist dev mode choice: " + e.getMessage());
        }
    }
}
