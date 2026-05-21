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
    private static final Path DEV_WORKSPACE_PATH = Path.of(
            System.getProperty("user.home"), ".namdesktop", "dev", "workspace.json");

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
        var workspacePath = devMode ? DEV_WORKSPACE_PATH : WORKSPACE_PATH;

        var repository = new JsonWorkspaceRepository();
        var workspace = loadWorkspace(repository, workspacePath);
        var service = new NamWorkspaceService(workspace, repository, workspacePath);
        var frame = new MainFrame(workspace, service, devMode);
        frame.setTitle(AppInfo.NAME + " " + AppInfo.version() + (devMode ? " [DEV]" : ""));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static NamWorkspace loadWorkspace(JsonWorkspaceRepository repository, Path path) {
        try {
            return repository.load(path);
        } catch (Exception e) {
            System.err.println("Failed to load workspace, starting with default: " + e.getMessage());
            return NamWorkspace.createDefault();
        }
    }
}