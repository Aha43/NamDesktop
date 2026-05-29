package namdesktop.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.app.AppSettings;
import namdesktop.demo.NamDemoWiring;
import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;
import namdesktop.sync.WorkspaceSyncService;
import swingdemo.ScriptRunner;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

public final class MainFrame extends JFrame {

    private static List<NavigationEntry> buildNavEntries(boolean devMode) {
        var entries = new java.util.ArrayList<>(List.of(
                new NavigationEntry("inbox",        "Inbox",        "Capture everything — process and clarify later"),
                new NavigationEntry("projects",     "Projects",     "Multi-step outcomes that require more than one action"),
                new NavigationEntry("next-actions", "Next Actions", "Concrete physical actions you can do right now"),
                new NavigationEntry("context",      "Tag filter",   "Filter your actions by tag"),
                new NavigationEntry("backlog",      "Backlog",      "Actions deferred for later — not yet the right time"),
                new NavigationEntry("blocked",      "Blocked",      "Actions waiting on a prerequisite to be done"),
                new NavigationEntry("done",         "Done",         "Completed actions — review and clean up")
        ));
        if (devMode) entries.add(new NavigationEntry("raw-tree", "Raw Tree", "Developer view: raw node tree"));
        return List.copyOf(entries);
    }

    private final NamWorkspace        workspace;
    private final NamWorkspaceService service;
    private final AppSettings         settings;
    private final WorkspaceSyncService syncService;
    private final Path                workspacePath;
    private final ContentArea         contentArea;
    private final NavigationPanel  navPanel;
    private final TreePanel        treePanel;
    private final InboxPanel       inboxPanel;
    private final ProjectsPanel    projectsPanel;
    private final NextActionsPanel nextActionsPanel;
    private final ContextPanel     contextPanel;
    private final BacklogPanel     backlogPanel;
    private final DonePanel        donePanel;
    private final BlockedPanel     blockedPanel;
    private final SearchPanel      searchPanel;
    private final HelpPanel        helpPanel;
    private final JLabel           demoStatusBar;
    private       Timer            nudgeTimer;
    private static java.util.function.Consumer<String> nudgeCallback = msg -> {};
    private final JSplitPane            splitPane;
    private       int                   lastNavDivider = 180;
    private       ProjectWorkbenchPanel cachedWorkbench;
    private       java.util.UUID        cachedWorkbenchId;
    private       boolean               sessionRestored  = false;
    private final boolean               devMode;
    private       boolean               monitoringActive = false;
    private       JButton               monitoringButton;
    private       JLabel                monitoringIndicator;
    private       JButton               checkpointButton;
    private       JMenuItem             checkpointItem;
    private       namdesktop.service.ExternalWorkspaceWatcher externalWatcher;

    public MainFrame(NamWorkspace workspace, NamWorkspaceService service, boolean devMode, AppSettings settings, WorkspaceSyncService syncService, Path workspacePath) {
        this.workspace        = workspace;
        this.service          = service;
        this.settings         = settings;
        this.syncService      = syncService;
        this.workspacePath    = workspacePath;
        this.devMode          = devMode;
        this.contentArea      = new ContentArea();
        this.treePanel        = new TreePanel(workspace, service, this::refreshAll);
        this.inboxPanel       = new InboxPanel(workspace, service);
        this.projectsPanel    = new ProjectsPanel(workspace, service, this::openProjectWorkbench);
        this.nextActionsPanel = new NextActionsPanel(workspace, service, this::openProjectWorkbench);
        this.contextPanel     = new ContextPanel(workspace, service, this::rebuildDynamicNavSections);
        this.backlogPanel     = new BacklogPanel(workspace, service, this::openProjectWorkbench);
        this.donePanel        = new DonePanel(workspace, service, this::openProjectWorkbench);
        this.blockedPanel     = new BlockedPanel(workspace, service, this::openProjectWorkbench);
        this.searchPanel      = new SearchPanel(workspace, service);
        this.helpPanel        = new HelpPanel();

        this.demoStatusBar = new JLabel(" ");
        this.demoStatusBar.setFont(demoStatusBar.getFont().deriveFont(Font.ITALIC));
        this.demoStatusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        this.demoStatusBar.setVisible(false);
        nudgeCallback = this::displayNudge;

        this.navPanel = new NavigationPanel(buildNavEntries(devMode), this::onNavSelected);
        this.splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navPanel, contentArea);
        splitPane.setDividerLocation(180);
        splitPane.setResizeWeight(0.0);
        splitPane.setOneTouchExpandable(true);

        var toolbar = new JToolBar();
        toolbar.setFloatable(false);
        var manageTagsButton = UiHelper.iconButton("Manage Tags…", new FlatSVGIcon(MainFrame.class.getResource("/icons/tag.svg")).derive(16, 16));
        manageTagsButton.addActionListener(e ->
                new TagManagementDialog(this, workspace, service, this::refreshAll).setVisible(true));
        toolbar.add(manageTagsButton);
        var captureButton = UiHelper.iconButton("Capture to Inbox",
                new FlatSVGIcon(MainFrame.class.getResource("/icons/inbox.svg")).derive(16, 16));
        captureButton.setToolTipText("Capture to Inbox (" +
                (java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() == java.awt.event.InputEvent.META_DOWN_MASK ? "⌘" : "Ctrl") +
                "+I)");
        captureButton.addActionListener(e -> inboxPanel.triggerAdd());
        toolbar.add(captureButton);
        var searchButton = UiHelper.iconButton("Search", new FlatSVGIcon(MainFrame.class.getResource("/icons/search.svg")).derive(16, 16));
        searchButton.addActionListener(e -> openSearch());
        toolbar.add(searchButton);
        var newMcButton = UiHelper.iconButton("New Goal Board…",
                new FlatSVGIcon(MainFrame.class.getResource("/icons/layout-dashboard.svg")).derive(16, 16));
        newMcButton.setToolTipText("New Goal Board…");
        newMcButton.addActionListener(e -> createGoalBoard());
        toolbar.add(newMcButton);
        if (!devMode && syncService.isConfigured()) {
            var pushButton = UiHelper.iconButton("Push workspace", new FlatSVGIcon(MainFrame.class.getResource("/icons/cloud-upload.svg")).derive(16, 16));
            pushButton.addActionListener(e -> runSync(true));
            var pullButton = UiHelper.iconButton("Pull workspace", new FlatSVGIcon(MainFrame.class.getResource("/icons/cloud-download.svg")).derive(16, 16));
            pullButton.addActionListener(e -> runSync(false));
            toolbar.add(pushButton);
            toolbar.add(pullButton);
        }
        monitoringButton = UiHelper.iconButton("Toggle Monitoring Mode",
                new FlatSVGIcon(MainFrame.class.getResource("/icons/antenna.svg")).derive(16, 16));
        monitoringButton.setToolTipText("Enter monitoring mode (Cmd+Shift+M)");
        monitoringButton.setEnabled(workspacePath != null);
        monitoringButton.addActionListener(e -> toggleMonitoringMode());
        toolbar.add(monitoringButton);
        monitoringIndicator = new JLabel("● Monitoring");
        monitoringIndicator.setForeground(new Color(0xE6, 0x8A, 0x00));
        monitoringIndicator.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        monitoringIndicator.setVisible(false);
        toolbar.add(monitoringIndicator);
        checkpointButton = UiHelper.iconButton("Checkpoint",
                new FlatSVGIcon(MainFrame.class.getResource("/icons/check.svg")).derive(16, 16));
        checkpointButton.setToolTipText("Checkpoint — flush changes to workspace, stay in monitoring (Cmd+Shift+S)");
        checkpointButton.setVisible(false);
        checkpointButton.addActionListener(e -> checkpoint());
        toolbar.add(checkpointButton);
        var helpButton = UiHelper.iconButton("Help",
                new FlatSVGIcon(MainFrame.class.getResource("/icons/help.svg")).derive(16, 16));
        helpButton.setToolTipText("Help — tutorials and concept reference");
        helpButton.addActionListener(e -> contentArea.setContent(helpPanel));
        toolbar.add(helpButton);
        toolbar.add(Box.createHorizontalGlue());
        if (devMode) {
            var devIndicator = new JLabel("● Dev");
            devIndicator.setForeground(new Color(0xE6, 0x8A, 0x00));
            devIndicator.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            toolbar.add(devIndicator);
        }
        var settingsButton = UiHelper.iconButton("Settings…",
                new FlatSVGIcon(MainFrame.class.getResource("/icons/settings.svg")).derive(16, 16));
        settingsButton.setToolTipText("Settings");
        settingsButton.addActionListener(e -> openSettings());
        toolbar.add(settingsButton);
        var exitButton = UiHelper.iconButton("Exit",
                new FlatSVGIcon(MainFrame.class.getResource("/icons/logout.svg")).derive(16, 16));
        exitButton.setToolTipText("Exit NamDesktop");
        exitButton.addActionListener(e -> confirmAndExit());
        toolbar.add(exitButton);

        var manageTagsItem = new JMenuItem("Manage Tags…");
        manageTagsItem.addActionListener(e ->
                new TagManagementDialog(this, workspace, service, this::refreshAll).setVisible(true));
        var searchItem = new JMenuItem("Search…");
        searchItem.addActionListener(e -> openSearch());
        var settingsItem = new JMenuItem("Settings…");
        settingsItem.addActionListener(e -> openSettings());
        var templatesItem = new JMenuItem("Templates…");
        templatesItem.addActionListener(e -> new TemplatesDialog(this, workspace, service).setVisible(true));
        var newMcItem = new JMenuItem("New Goal Board…");
        newMcItem.addActionListener(e -> createGoalBoard());

        var captureItem = new JMenuItem("Capture to Inbox");
        captureItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I,
                java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        captureItem.addActionListener(e -> inboxPanel.triggerAdd());

        var monitoringItem = new JMenuItem("Toggle Monitoring Mode");
        monitoringItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M,
                java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
                        | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        monitoringItem.setEnabled(workspacePath != null);
        monitoringItem.addActionListener(e -> toggleMonitoringMode());
        checkpointItem = new JMenuItem("Checkpoint");
        checkpointItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
                java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
                        | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        checkpointItem.setEnabled(false);
        checkpointItem.addActionListener(e -> checkpoint());

        var fileMenu = new JMenu("File");
        fileMenu.add(captureItem);
        fileMenu.add(monitoringItem);
        fileMenu.add(checkpointItem);
        fileMenu.addSeparator();
        fileMenu.add(manageTagsItem);
        fileMenu.add(searchItem);
        fileMenu.add(templatesItem);
        fileMenu.add(newMcItem);
        var runDemoItem = new JMenuItem("Run Demo…");
        runDemoItem.addActionListener(e -> runDemo());
        fileMenu.add(runDemoItem);
        fileMenu.addSeparator();

        if (!devMode && syncService.isConfigured()) {
            var pushItem = new JMenuItem("Push workspace");
            pushItem.addActionListener(e -> runSync(true));
            var pullItem = new JMenuItem("Pull workspace");
            pullItem.addActionListener(e -> runSync(false));
            fileMenu.add(pushItem);
            fileMenu.add(pullItem);
            fileMenu.addSeparator();
        }

        fileMenu.add(settingsItem);
        fileMenu.addSeparator();
        var exitItem = new JMenuItem("Exit");
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q,
                java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        exitItem.addActionListener(e -> confirmAndExit());
        fileMenu.add(exitItem);
        var toolbarToggleItem = new JMenuItem(settings.isShowToolbar() ? "Hide Toolbar" : "Show Toolbar");
        toolbarToggleItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T,
                java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        final JMenuItem[] zenRef = {null};
        toolbarToggleItem.addActionListener(e -> {
            var show = !toolbar.isVisible();
            toolbar.setVisible(show);
            toolbarToggleItem.setText(show ? "Hide Toolbar" : "Show Toolbar");
            settings.setShowToolbar(show);
            saveSession();
            if (zenRef[0] != null) zenRef[0].setText(
                    !toolbar.isVisible() && splitPane.getDividerLocation() == 0
                            ? "Exit Zen Mode" : "Enter Zen Mode");
        });
        var navToggleItem = new JMenuItem(settings.isShowNavPane() ? "Hide Nav Pane" : "Show Nav Pane");
        navToggleItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N,
                java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        navToggleItem.addActionListener(e -> {
            var show = splitPane.getDividerLocation() == 0;
            if (!show) lastNavDivider = splitPane.getDividerLocation();
            splitPane.setDividerLocation(show ? lastNavDivider : 0);
        });
        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            var shown = (int) e.getNewValue() > 0;
            navToggleItem.setText(shown ? "Hide Nav Pane" : "Show Nav Pane");
            settings.setShowNavPane(shown);
            saveSession();
        });
        var zenItem = new JMenuItem("Enter Zen Mode");
        zenRef[0] = zenItem;
        zenItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z,
                java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        zenItem.addActionListener(e -> {
            var inZen = !toolbar.isVisible() && splitPane.getDividerLocation() == 0;
            if (inZen) {
                toolbar.setVisible(true);
                toolbarToggleItem.setText("Hide Toolbar");
                settings.setShowToolbar(true);
                splitPane.setDividerLocation(lastNavDivider);  // property listener updates navToggleItem
            } else {
                if (splitPane.getDividerLocation() > 0) lastNavDivider = splitPane.getDividerLocation();
                toolbar.setVisible(false);
                toolbarToggleItem.setText("Show Toolbar");
                settings.setShowToolbar(false);
                splitPane.setDividerLocation(0);               // property listener updates navToggleItem
            }
            zenItem.setText(inZen ? "Enter Zen Mode" : "Exit Zen Mode");
            saveSession();
        });
        // Keep zen item label in sync when toolbar or nav are toggled individually
        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, ev ->
                zenItem.setText(!toolbar.isVisible() && (int) ev.getNewValue() == 0
                        ? "Exit Zen Mode" : "Enter Zen Mode"));
        var viewMenu = new JMenu("View");
        viewMenu.add(toolbarToggleItem);
        viewMenu.add(navToggleItem);
        viewMenu.addSeparator();
        viewMenu.add(zenItem);

        var helpMenuItem = new JMenuItem("Help");
        helpMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        helpMenuItem.addActionListener(e -> contentArea.setContent(helpPanel));
        var aboutItem = new JMenuItem("About " + namdesktop.app.AppInfo.NAME + "…");
        aboutItem.addActionListener(e -> new AboutDialog(this).setVisible(true));
        var helpMenu = new JMenu("Help");
        helpMenu.add(helpMenuItem);
        helpMenu.addSeparator();
        helpMenu.add(aboutItem);

        var menuBar = new JMenuBar();
        menuBar.add(fileMenu);
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);

        if (Desktop.isDesktopSupported()) {
            var desktop = Desktop.getDesktop();
            try { desktop.setPreferencesHandler(e -> openSettings()); }
            catch (UnsupportedOperationException ignored) {}
            try { desktop.setQuitHandler((e, r) -> confirmAndExit()); }
            catch (UnsupportedOperationException ignored) {}
        }

        toolbar.setVisible(settings.isShowToolbar());
        if (!settings.isShowNavPane()) SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(0));
        add(toolbar,        BorderLayout.NORTH);
        add(splitPane,      BorderLayout.CENTER);
        add(demoStatusBar,  BorderLayout.SOUTH);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { confirmAndExit(); }
        });
        setSize(900, 600);

        navPanel.rebuildDynamicSections(workspace.getSavedViews(), workspace.getMissionControls());
    }

    public static void showNudge(String message) { nudgeCallback.accept(message); }

    private void toggleMonitoringMode() {
        if (workspacePath == null) return;
        if (!monitoringActive) {
            try {
                namdesktop.service.MonitoringMode.enter(workspacePath);
                startWatcher();
                monitoringActive = true;
                updateMonitoringUI();
            } catch (java.io.IOException e) {
                JOptionPane.showMessageDialog(this, "Failed to enter monitoring mode: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            exitMonitoringMode();
        }
    }

    private void startWatcher() throws java.io.IOException {
        var repo    = new namdesktop.persist.JsonWorkspaceRepository();
        var initial = repo.load(namdesktop.service.MonitoringMode.externalPath(workspacePath));
        externalWatcher = new namdesktop.service.ExternalWorkspaceWatcher(
                workspacePath, repo, summary -> SwingUtilities.invokeLater(() -> onExternalChange(summary)));
        externalWatcher.start(initial);
    }

    private void checkpoint() {
        if (!monitoringActive || workspacePath == null) return;
        if (externalWatcher != null) { externalWatcher.stop(); externalWatcher = null; }
        var repo   = new namdesktop.persist.JsonWorkspaceRepository();
        var result = namdesktop.service.MonitoringMode.exit(workspacePath, repo);
        if (result instanceof namdesktop.service.MonitoringMode.ExitResult.NoChanges) {
            showNudge("No changes to checkpoint.");
        } else if (result instanceof namdesktop.service.MonitoringMode.ExitResult.HasChanges hc) {
            var accepted = MonitoringExitDialog.show(this, hc.summary(), "Checkpoint");
            if (accepted) {
                try {
                    namdesktop.service.MonitoringMode.checkpointAccept(workspacePath);
                    service.reloadWorkspace();
                    refreshAll();
                    showNudge("Checkpoint — changes saved, monitoring continues.");
                } catch (java.io.IOException e) {
                    JOptionPane.showMessageDialog(this, "Checkpoint failed: " + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                try {
                    namdesktop.service.MonitoringMode.checkpointReject(workspacePath);
                } catch (java.io.IOException ignored) {}
                showNudge("Checkpoint — changes discarded, monitoring continues.");
            }
        } else if (result instanceof namdesktop.service.MonitoringMode.ExitResult.Unparseable up) {
            var choice = JOptionPane.showConfirmDialog(this,
                    "External workspace is unreadable: " + up.error()
                            + "\n\nReset it to current workspace and continue?",
                    "Checkpoint", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                try { namdesktop.service.MonitoringMode.checkpointReject(workspacePath); }
                catch (java.io.IOException ignored) {}
            }
        }
        try { startWatcher(); } catch (java.io.IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to restart file watcher: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            monitoringActive = false;
            updateMonitoringUI();
        }
    }

    private void onExternalChange(namdesktop.service.MonitoringMode.DiffSummary summary) {
        try {
            service.reloadWorkspaceFrom(namdesktop.service.MonitoringMode.externalPath(workspacePath));
            refreshAll();
        } catch (java.io.IOException ignored) {}
        showNudge(summary.describe());
        if (summary.inboxAdded() > 0) {
            navPanel.selectById("inbox");
            onNavSelected(new NavigationEntry("inbox", "Inbox", ""));
        }
    }

    private void exitMonitoringMode() {
        if (externalWatcher != null) { externalWatcher.stop(); externalWatcher = null; }
        var repo   = new namdesktop.persist.JsonWorkspaceRepository();
        var result = namdesktop.service.MonitoringMode.exit(workspacePath, repo);
        if (result instanceof namdesktop.service.MonitoringMode.ExitResult.NoChanges) {
            namdesktop.service.MonitoringMode.reject(workspacePath);
            monitoringActive = false;
            updateMonitoringUI();
        } else if (result instanceof namdesktop.service.MonitoringMode.ExitResult.HasChanges hc) {
            var accepted = MonitoringExitDialog.show(this, hc.summary());
            if (accepted) {
                try {
                    namdesktop.service.MonitoringMode.accept(workspacePath);
                    service.reloadWorkspace();
                    refreshAll();
                } catch (java.io.IOException e) {
                    JOptionPane.showMessageDialog(this, "Failed to apply changes: " + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } else {
                namdesktop.service.MonitoringMode.reject(workspacePath);
                try { service.reloadWorkspace(); refreshAll(); } catch (java.io.IOException ignored) {}
            }
            monitoringActive = false;
            updateMonitoringUI();
        } else if (result instanceof namdesktop.service.MonitoringMode.ExitResult.Unparseable up) {
            var choice = JOptionPane.showConfirmDialog(this,
                    "External workspace file is unreadable: " + up.error()
                            + "\n\nReject and discard it?",
                    "Exit Monitoring Mode", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                namdesktop.service.MonitoringMode.reject(workspacePath);
                monitoringActive = false;
                updateMonitoringUI();
            }
        }
    }

    private void updateMonitoringUI() {
        var baseTitle = getTitle().replaceAll(" \\[Monitoring\\]$", "");
        setTitle(monitoringActive ? baseTitle + " [Monitoring]" : baseTitle);
        monitoringButton.setToolTipText(monitoringActive
                ? "Exit monitoring mode (Cmd+Shift+M)"
                : "Enter monitoring mode (Cmd+Shift+M)");
        monitoringIndicator.setVisible(monitoringActive);
        checkpointButton.setVisible(monitoringActive);
        checkpointItem.setEnabled(monitoringActive);
    }

    private void confirmAndExit() {
        if (monitoringActive) {
            exitMonitoringMode();
            if (monitoringActive) return; // user cancelled (unparseable + chose not to reject)
        }
        System.exit(0);
    }

    private void openSettings() {
        new SettingsDialog(this, settings, () -> {
            nextActionsPanel.applyColumnVisibility(settings.isShowStatusColumn());
            backlogPanel.applyColumnVisibility(settings.isShowStatusColumn());
        }).setVisible(true);
    }

    private void displayNudge(String message) {
        if (nudgeTimer != null) nudgeTimer.stop();
        demoStatusBar.setText(message);
        demoStatusBar.setVisible(true);
        nudgeTimer = new Timer(4000, e -> demoStatusBar.setVisible(false));
        nudgeTimer.setRepeats(false);
        nudgeTimer.start();
    }

    private void saveSession() {
        try { settings.save(); } catch (java.io.IOException ignored) {}
    }

    private void onNavSelected(NavigationEntry entry) {
        if (sessionRestored) {
            settings.setLastNavId(entry.id());
            settings.setLastProjectId(null);
            saveSession();
        }
        if (entry.id().startsWith("mc:")) {
            var name = entry.id().substring("mc:".length());
            workspace.getMissionControls().stream()
                    .filter(mc -> mc.name().equals(name))
                    .findFirst()
                    .ifPresent(mc -> {
                        final MissionControlPanel[] ref = {null};
                        ref[0] = new MissionControlPanel(mc, workspace, service,
                                id -> {
                                    if (cachedWorkbench == null || !id.equals(cachedWorkbenchId)) {
                                        cachedWorkbenchId = id;
                                        cachedWorkbench   = new ProjectWorkbenchPanel(this, workspace, service, id, mc.name(), () -> {
                                            cachedWorkbench   = null;
                                            cachedWorkbenchId = null;
                                            contentArea.setContent(ref[0]);
                                            ref[0].refresh();
                                        });
                                    }
                                    contentArea.setContent(cachedWorkbench);
                                },
                                () -> {
                                    rebuildDynamicNavSections();
                                    navPanel.selectById("projects");
                                });
                        contentArea.setContent(ref[0]);
                    });
            return;
        }
        if (entry.id().startsWith("saved-view:")) {
            var name = entry.id().substring("saved-view:".length());
            workspace.getSavedViews().stream()
                    .filter(sv -> sv.name().equals(name))
                    .findFirst()
                    .ifPresent(sv -> contentArea.setContent(new SavedViewPanel(sv, workspace, service,
                            () -> {
                                rebuildDynamicNavSections();
                                navPanel.selectById("context");
                            },
                            newName -> {
                                rebuildDynamicNavSections();
                                navPanel.selectById("saved-view:" + newName);
                            })));
            return;
        }
        switch (entry.id()) {
            case "inbox"         -> { contentArea.setContent(inboxPanel);        inboxPanel.refresh(); }
            case "projects"      -> { contentArea.setContent(projectsPanel);     projectsPanel.refresh(); }
            case "next-actions"  -> { contentArea.setContent(nextActionsPanel);  nextActionsPanel.refresh(); }
            case "context"       -> { contentArea.setContent(contextPanel);      contextPanel.refresh(); }
            case "backlog"       -> { contentArea.setContent(backlogPanel);      backlogPanel.refresh(); }
            case "blocked"       -> { contentArea.setContent(blockedPanel);     blockedPanel.refresh(); }
            case "done"          -> { contentArea.setContent(donePanel);         donePanel.refresh(); }
            case "raw-tree"      -> contentArea.setContent(treePanel);
            default              -> contentArea.setContent(placeholder(entry.title()));
        }
    }

    private void runSync(boolean push) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new Thread(() -> {
            String message;
            boolean success;
            try {
                if (push) syncService.push(workspacePath);
                else      syncService.pull(workspacePath);
                message = push ? "Workspace pushed successfully."
                               : "Workspace pulled successfully.\nRestart the app to apply the updated workspace.";
                success = true;
            } catch (Exception ex) {
                message = ex.getMessage();
                success = false;
            }
            final var msg     = message;
            final var isOk    = success;
            SwingUtilities.invokeLater(() -> {
                setCursor(Cursor.getDefaultCursor());
                if (isOk && !push) {
                    int choice = JOptionPane.showOptionDialog(this, msg, "Pull",
                            JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                            new Object[]{"Exit now", "Later"}, "Exit now");
                    if (choice == 0) System.exit(0);
                } else if (isOk) {
                    JOptionPane.showMessageDialog(this, msg, "Push", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, msg, "Sync error", JOptionPane.ERROR_MESSAGE);
                }
            });
        }, "sync-thread").start();
    }

    private void openSearch() {
        contentArea.setContent(searchPanel);
        searchPanel.refresh();
    }

    private void openProjectWorkbench(java.util.UUID projectId) {
        if (sessionRestored) {
            settings.setLastProjectId(projectId.toString());
            saveSession();
        }
        if (cachedWorkbench == null || !projectId.equals(cachedWorkbenchId)) {
            cachedWorkbenchId = projectId;
            cachedWorkbench   = new ProjectWorkbenchPanel(this, workspace, service, projectId, () -> {
                cachedWorkbench   = null;
                cachedWorkbenchId = null;
                contentArea.setContent(projectsPanel);
                projectsPanel.refresh();
            });
        }
        contentArea.setContent(cachedWorkbench);
    }

    public void runDemo() {
        var script = MainFrame.class.getResourceAsStream("/demo.json");
        if (script == null) {
            JOptionPane.showMessageDialog(this, "demo.json not found in JAR.", "Demo", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (workspace.getNodes().size() > 4) {
            var choice = JOptionPane.showConfirmDialog(this,
                    "Running the demo will replace your current workspace with sample data.\n"
                    + "Any items you have added will be lost.\n\nContinue?",
                    "Run Demo", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) return;
        }
        try {
            service.resetWorkspaceToDefault();
            refreshAll();
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to reset workspace: " + ex.getMessage(), "Demo", JOptionPane.ERROR_MESSAGE);
            return;
        }
        demoStatusBar.setVisible(true);
        var runner = new ScriptRunner(new ObjectMapper(), this::refreshAll);
        new NamDemoWiring(workspace, service).configure(runner);
        runner
            .setOnStep(step -> {
                var desc = step.description();
                demoStatusBar.setText(desc.isEmpty() ? " " : desc);
            })
            .setOnComplete(() -> {
                demoStatusBar.setText("Demo complete.");
                javax.swing.Timer hideTimer = new javax.swing.Timer(3000, e -> demoStatusBar.setVisible(false));
                hideTimer.setRepeats(false);
                hideTimer.start();
                refreshAll();
            });
        try {
            runner.run(script);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Demo failed: " + ex.getMessage(), "Demo", JOptionPane.ERROR_MESSAGE);
            demoStatusBar.setVisible(false);
        }
    }

    public void restoreSession() {
        if (!settings.isWelcomed()) {
            contentArea.setContent(new WelcomePanel(
                    () -> { markWelcomed(); runDemo(); },
                    () -> { markWelcomed(); navPanel.selectById("inbox"); }));
            sessionRestored = true;
            return;
        }

        var navId     = settings.getLastNavId();
        var projectId = settings.getLastProjectId();

        if (navId != null) navPanel.selectById(navId);

        if (projectId != null) {
            try {
                var id = java.util.UUID.fromString(projectId);
                if (workspace.getNode(id).isPresent()) openProjectWorkbench(id);
            } catch (IllegalArgumentException ignored) {}
        }

        sessionRestored = true;
    }

    private void markWelcomed() {
        settings.setWelcomed(true);
        sessionRestored = true;
        saveSession();
    }

    public void refreshAll() {
        cachedWorkbench   = null;
        cachedWorkbenchId = null;
        var current = contentArea.getContent();
        if (current instanceof ProjectWorkbenchPanel pwp) pwp.refresh();
        inboxPanel.refresh();
        projectsPanel.refresh();
        nextActionsPanel.refresh();
        contextPanel.refresh();
        backlogPanel.refresh();
        blockedPanel.refresh();
        donePanel.refresh();
        rebuildDynamicNavSections();
    }

    private void rebuildDynamicNavSections() {
        navPanel.rebuildDynamicSections(workspace.getSavedViews(), workspace.getMissionControls());
    }

    private void createGoalBoard() {
        var dialog = new GoalBoardCreateDialog(this, workspace);
        dialog.setVisible(true);
        if (!dialog.isConfirmed()) return;
        var name = dialog.getGoalBoardName();
        var tags = dialog.getGoalBoardTags();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name must not be blank.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            service.createMissionControl(name, tags);
            rebuildDynamicNavSections();
            navPanel.selectById("mc:" + name);
        } catch (IllegalArgumentException | java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static JPanel placeholder(String label) {
        var panel = new JPanel(new BorderLayout());
        var text  = new JLabel(label, SwingConstants.CENTER);
        panel.add(text, BorderLayout.CENTER);
        return panel;
    }
}
