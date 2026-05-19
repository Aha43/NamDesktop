package namdesktop.app;

import namdesktop.model.NamWorkspace;
import namdesktop.persist.JsonWorkspaceRepository;
import namdesktop.ui.MainFrame;

import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;
import java.nio.file.Path;

public final class NamDesktopMain {

    private static final Path WORKSPACE_PATH = Path.of(
            System.getProperty("user.home"), ".namdesktop", "workspace.json");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(NamDesktopMain::start);
    }

    private static void start() {
        FlatLightLaf.setup();
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", AppInfo.NAME);

        var workspace = loadWorkspace();
        var frame = new MainFrame(workspace);
        frame.setTitle(AppInfo.NAME + " " + AppInfo.version());
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static NamWorkspace loadWorkspace() {
        try {
            return new JsonWorkspaceRepository().load(WORKSPACE_PATH);
        } catch (Exception e) {
            System.err.println("Failed to load workspace, starting with default: " + e.getMessage());
            return NamWorkspace.createDefault();
        }
    }
}