package namdesktop.app;

import namdesktop.model.NamWorkspace;
import namdesktop.persist.JsonWorkspaceRepository;
import namdesktop.service.NamWorkspaceService;
import namdesktop.ui.MainFrame;
import namdesktop.ui.SplashDialog;

import javax.swing.*;
import com.formdev.flatlaf.FlatDarkLaf;
import java.nio.file.Path;

public final class NamDesktopMain {

    private static final Path WORKSPACE_PATH = Path.of(
            System.getProperty("user.home"), ".namdesktop", "workspace.json");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(NamDesktopMain::start);
    }

    private static void start() {
        FlatDarkLaf.setup();
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", AppInfo.NAME);

        var splash = new SplashDialog();
        splash.setVisible(true);
        var devMode = splash.isDevMode();

        var repository = new JsonWorkspaceRepository();
        var workspace = loadWorkspace(repository);
        var service = new NamWorkspaceService(workspace, repository, WORKSPACE_PATH);
        var frame = new MainFrame(workspace, service);
        frame.setTitle(AppInfo.NAME + " " + AppInfo.version() + (devMode ? " [DEV]" : ""));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static NamWorkspace loadWorkspace(JsonWorkspaceRepository repository) {
        try {
            return repository.load(WORKSPACE_PATH);
        } catch (Exception e) {
            System.err.println("Failed to load workspace, starting with default: " + e.getMessage());
            return NamWorkspace.createDefault();
        }
    }
}