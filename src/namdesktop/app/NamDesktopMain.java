package namdesktop.app;

import namdesktop.demo.NamAssertWiring;
import namdesktop.demo.NamDemoWiring;
import namdesktop.model.NamWorkspace;
import namdesktop.persist.JsonWorkspaceRepository;
import namdesktop.service.NamWorkspaceService;
import namdesktop.sync.GitSyncService;
import namdesktop.ui.MainFrame;
import namdesktop.ui.SplashDialog;
import swingdemo.ScriptRunner;

import javax.swing.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.nio.file.Path;
import java.util.Arrays;

public final class NamDesktopMain {

    private static final Path WORKSPACE_PATH = Path.of(
            System.getProperty("user.home"), ".namdesktop", "workspace.json");
    private static final Path DEV_WORKSPACE_PATH = Path.of(
            System.getProperty("user.home"), ".namdesktop", "dev", "workspace.json");
    private static final Path SYNC_CLONE_DIR = Path.of(
            System.getProperty("user.home"), ".namdesktop", "sync");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> start(args));
    }

    private static void start(String[] args) {
        var argList = Arrays.asList(args);
        if (argList.contains("--e2e")) { startE2e(); return; }

        var settings = AppSettings.load();
        AppSettings.setInstance(settings);
        if (settings.getTheme() == Theme.LIGHT) FlatLightLaf.setup(); else FlatDarkLaf.setup();
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", AppInfo.NAME);

        var splash = new SplashDialog(settings);
        splash.setVisible(true);
        var devMode = splash.isDevMode();
        var workspacePath = devMode ? DEV_WORKSPACE_PATH : WORKSPACE_PATH;

        var repository  = new JsonWorkspaceRepository();
        var workspace   = loadWorkspace(repository, workspacePath);
        var service     = new NamWorkspaceService(workspace, repository, workspacePath);
        var syncService = new GitSyncService(settings.getSyncRepoUrl(), SYNC_CLONE_DIR);
        var frame = new MainFrame(workspace, service, devMode, settings, syncService, workspacePath);
        frame.setTitle(AppInfo.NAME + " " + AppInfo.version() + (devMode ? " [DEV]" : ""));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        if (settings.isStartMaximized()) frame.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
        if (argList.contains("--demo")) frame.runDemo();
    }

    private static void startE2e() {
        var settings = AppSettings.load();
        AppSettings.setInstance(settings);
        FlatDarkLaf.setup();
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", AppInfo.NAME + " E2E");

        var workspace   = NamWorkspace.createDefault();
        var repository  = new JsonWorkspaceRepository();
        var service     = new NamWorkspaceService(workspace, repository, null);
        var syncService = new GitSyncService(null, null);
        var frame       = new MainFrame(workspace, service, false, settings, syncService, null);
        frame.setTitle(AppInfo.NAME + " — E2E");
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        var script = NamDesktopMain.class.getResourceAsStream("/e2e.json");
        if (script == null) {
            System.err.println("[e2e] e2e.json not found in JAR");
            System.exit(1);
        }
        var runner = new ScriptRunner(new ObjectMapper(), frame::refreshAll);
        new NamDemoWiring(workspace, service).configure(runner);
        new NamAssertWiring(workspace).configure(runner);
        runner
            .setOnStep(step -> {
                if (!step.description().isEmpty())
                    System.out.println("[e2e] " + step.description());
            })
            .setOnComplete(() -> {
                int failures = runner.getFailureCount();
                if (failures == 0) {
                    System.out.println("[e2e] PASSED");
                    System.exit(0);
                } else {
                    System.out.println("[e2e] FAILED — " + failures + " step(s) failed");
                    System.exit(1);
                }
            });
        try {
            runner.run(script);
        } catch (Exception ex) {
            System.err.println("[e2e] Failed to read e2e.json: " + ex.getMessage());
            System.exit(1);
        }
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