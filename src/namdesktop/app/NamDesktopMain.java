package namdesktop.app;

import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;
import namdesktop.ui.MainFrame;

public final class NamDesktopMain {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(NamDesktopMain::start);
    }

    private static void start() {
        FlatLightLaf.setup();
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", AppInfo.NAME);

        var frame = new MainFrame();
        frame.setTitle(AppInfo.NAME + " " + AppInfo.version());
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}