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
                new NavigationEntry("context",      "Context",      "Filter your next actions by tag"),
                new NavigationEntry("backlog",      "Backlog",      "Actions deferred for later — not yet the right time"),
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
    private final SearchPanel      searchPanel;
    private final HelpPanel        helpPanel;
    private final JLabel           demoStatusBar;
    private       ProjectWorkbenchPanel cachedWorkbench;
    private       java.util.UUID        cachedWorkbenchId;
    private       boolean               sessionRestored = false;

    public MainFrame(NamWorkspace workspace, NamWorkspaceService service, boolean devMode, AppSettings settings, WorkspaceSyncService syncService, Path workspacePath) {
        this.workspace        = workspace;
        this.service          = service;
        this.settings         = settings;
        this.syncService      = syncService;
        this.workspacePath    = workspacePath;
        this.contentArea      = new ContentArea();
        this.treePanel        = new TreePanel(workspace, service, this::refreshAll);
        this.inboxPanel       = new InboxPanel(workspace, service);
        this.projectsPanel    = new ProjectsPanel(workspace, service, this::openProjectWorkbench);
        this.nextActionsPanel = new NextActionsPanel(workspace, service, this::openProjectWorkbench);
        this.contextPanel     = new ContextPanel(workspace, service, this::rebuildDynamicNavSections);
        this.backlogPanel     = new BacklogPanel(workspace, service, this::openProjectWorkbench);
        this.donePanel        = new DonePanel(workspace, service, this::openProjectWorkbench);
        this.searchPanel      = new SearchPanel(workspace, service);
        this.helpPanel        = new HelpPanel();

        this.demoStatusBar = new JLabel(" ");
        this.demoStatusBar.setFont(demoStatusBar.getFont().deriveFont(Font.ITALIC));
        this.demoStatusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        this.demoStatusBar.setVisible(false);

        this.navPanel = new NavigationPanel(buildNavEntries(devMode), this::onNavSelected);
        var splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navPanel, contentArea);
        splitPane.setDividerLocation(180);
        splitPane.setResizeWeight(0.0);
        splitPane.setOneTouchExpandable(true);

        var toolbar = new JToolBar();
        toolbar.setFloatable(false);
        var manageTagsButton = UiHelper.iconButton("Manage Tags…", new FlatSVGIcon(MainFrame.class.getResource("/icons/tag.svg")).derive(16, 16));
        manageTagsButton.addActionListener(e ->
                new TagManagementDialog(this, workspace, service, this::refreshAll).setVisible(true));
        toolbar.add(manageTagsButton);
        var searchButton = UiHelper.iconButton("Search", new FlatSVGIcon(MainFrame.class.getResource("/icons/search.svg")).derive(16, 16));
        searchButton.addActionListener(e -> openSearch());
        toolbar.add(searchButton);
        var newMcButton = UiHelper.iconButton("New Mission Control…",
                new FlatSVGIcon(MainFrame.class.getResource("/icons/layout-dashboard.svg")).derive(16, 16));
        newMcButton.setToolTipText("New Mission Control…");
        newMcButton.addActionListener(e -> createMissionControl());
        toolbar.add(newMcButton);
        if (!devMode && syncService.isConfigured()) {
            var pushButton = UiHelper.iconButton("Push workspace", new FlatSVGIcon(MainFrame.class.getResource("/icons/cloud-upload.svg")).derive(16, 16));
            pushButton.addActionListener(e -> runSync(true));
            var pullButton = UiHelper.iconButton("Pull workspace", new FlatSVGIcon(MainFrame.class.getResource("/icons/cloud-download.svg")).derive(16, 16));
            pullButton.addActionListener(e -> runSync(false));
            toolbar.add(pushButton);
            toolbar.add(pullButton);
        }
        var helpButton = UiHelper.iconButton("Help",
                new FlatSVGIcon(MainFrame.class.getResource("/icons/help.svg")).derive(16, 16));
        helpButton.setToolTipText("Help — tutorials and concept reference");
        helpButton.addActionListener(e -> contentArea.setContent(helpPanel));
        toolbar.add(helpButton);
        toolbar.add(Box.createHorizontalGlue());
        var exitButton = UiHelper.iconButton("Exit",
                new FlatSVGIcon(MainFrame.class.getResource("/icons/logout.svg")).derive(16, 16));
        exitButton.setToolTipText("Exit NamDesktop");
        exitButton.addActionListener(e -> System.exit(0));
        toolbar.add(exitButton);

        var manageTagsItem = new JMenuItem("Manage Tags…");
        manageTagsItem.addActionListener(e ->
                new TagManagementDialog(this, workspace, service, this::refreshAll).setVisible(true));
        var searchItem = new JMenuItem("Search…");
        searchItem.addActionListener(e -> openSearch());
        var settingsItem = new JMenuItem("Settings…");
        settingsItem.addActionListener(e -> new SettingsDialog(this, settings, () -> {
            nextActionsPanel.applyColumnVisibility(settings.isShowStatusColumn());
            backlogPanel.applyColumnVisibility(settings.isShowStatusColumn());
        }).setVisible(true));
        var templatesItem = new JMenuItem("Templates…");
        templatesItem.addActionListener(e -> new TemplatesDialog(this, workspace, service).setVisible(true));
        var newMcItem = new JMenuItem("New Mission Control…");
        newMcItem.addActionListener(e -> createMissionControl());

        var fileMenu = new JMenu("File");
        fileMenu.add(manageTagsItem);
        fileMenu.add(searchItem);
        fileMenu.add(templatesItem);
        fileMenu.add(newMcItem);
        if (devMode) {
            var runDemoItem = new JMenuItem("Run Demo…");
            runDemoItem.addActionListener(e -> runDemo());
            fileMenu.add(runDemoItem);
        }
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
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        var toolbarToggleItem = new JMenuItem(settings.isShowToolbar() ? "Hide Toolbar" : "Show Toolbar");
        toolbarToggleItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T,
                java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        toolbarToggleItem.addActionListener(e -> {
            var show = !toolbar.isVisible();
            toolbar.setVisible(show);
            toolbarToggleItem.setText(show ? "Hide Toolbar" : "Show Toolbar");
            settings.setShowToolbar(show);
            saveSession();
        });
        var viewMenu = new JMenu("View");
        viewMenu.add(toolbarToggleItem);

        var menuBar = new JMenuBar();
        menuBar.add(fileMenu);
        menuBar.add(viewMenu);

        setJMenuBar(menuBar);
        toolbar.setVisible(settings.isShowToolbar());
        add(toolbar,        BorderLayout.NORTH);
        add(splitPane,      BorderLayout.CENTER);
        add(demoStatusBar,  BorderLayout.SOUTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);

        navPanel.rebuildDynamicSections(workspace.getSavedViews(), workspace.getMissionControls());
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

    public void refreshAll() {
        cachedWorkbench   = null;
        cachedWorkbenchId = null;
        inboxPanel.refresh();
        projectsPanel.refresh();
        nextActionsPanel.refresh();
        contextPanel.refresh();
        backlogPanel.refresh();
        donePanel.refresh();
        rebuildDynamicNavSections();
    }

    private void rebuildDynamicNavSections() {
        navPanel.rebuildDynamicSections(workspace.getSavedViews(), workspace.getMissionControls());
    }

    private void createMissionControl() {
        var dialog = new MissionControlCreateDialog(this, workspace);
        dialog.setVisible(true);
        if (!dialog.isConfirmed()) return;
        var name = dialog.getMcName();
        var tags = dialog.getMcTags();
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
