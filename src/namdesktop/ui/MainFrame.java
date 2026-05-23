package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.ui.UiHelper;
import namdesktop.app.AppSettings;
import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;
import namdesktop.sync.WorkspaceSyncService;

import javax.swing.*;
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
                new NavigationEntry("backlog",      "Backlog",      "Actions deferred for later — not yet the right time")
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
    private final SearchPanel      searchPanel;

    public MainFrame(NamWorkspace workspace, NamWorkspaceService service, boolean devMode, AppSettings settings, WorkspaceSyncService syncService, Path workspacePath) {
        this.workspace        = workspace;
        this.service          = service;
        this.settings         = settings;
        this.syncService      = syncService;
        this.workspacePath    = workspacePath;
        this.contentArea      = new ContentArea();
        this.treePanel        = new TreePanel(workspace, service);
        this.inboxPanel       = new InboxPanel(workspace, service);
        this.projectsPanel    = new ProjectsPanel(workspace, service, this::openProjectWorkbench);
        this.nextActionsPanel = new NextActionsPanel(workspace, service);
        this.contextPanel     = new ContextPanel(workspace, service, this::rebuildSavedViewsNav);
        this.backlogPanel     = new BacklogPanel(workspace, service);
        this.searchPanel      = new SearchPanel(workspace, service);

        this.navPanel = new NavigationPanel(buildNavEntries(devMode), this::onNavSelected);
        var splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navPanel, contentArea);
        splitPane.setDividerLocation(180);
        splitPane.setResizeWeight(0.0);

        var toolbar = new JToolBar();
        toolbar.setFloatable(false);
        var manageTagsButton = UiHelper.iconButton("Manage Tags…", new FlatSVGIcon(MainFrame.class.getResource("/icons/tag.svg")).derive(16, 16));
        manageTagsButton.addActionListener(e ->
                new TagManagementDialog(this, workspace, service).setVisible(true));
        toolbar.add(manageTagsButton);
        var searchButton = UiHelper.iconButton("Search", new FlatSVGIcon(MainFrame.class.getResource("/icons/search.svg")).derive(16, 16));
        searchButton.addActionListener(e -> openSearch());
        toolbar.add(searchButton);
        if (!devMode && syncService.isConfigured()) {
            var pushButton = UiHelper.iconButton("Push workspace", new FlatSVGIcon(MainFrame.class.getResource("/icons/cloud-upload.svg")).derive(16, 16));
            pushButton.addActionListener(e -> runSync(true));
            var pullButton = UiHelper.iconButton("Pull workspace", new FlatSVGIcon(MainFrame.class.getResource("/icons/cloud-download.svg")).derive(16, 16));
            pullButton.addActionListener(e -> runSync(false));
            toolbar.add(pushButton);
            toolbar.add(pullButton);
        }
        toolbar.add(Box.createHorizontalGlue());
        var exitButton = UiHelper.iconButton("Exit",
                new FlatSVGIcon(MainFrame.class.getResource("/icons/logout.svg")).derive(16, 16));
        exitButton.setToolTipText("Exit NamDesktop");
        exitButton.addActionListener(e -> System.exit(0));
        toolbar.add(exitButton);

        var manageTagsItem = new JMenuItem("Manage Tags…");
        manageTagsItem.addActionListener(e ->
                new TagManagementDialog(this, workspace, service).setVisible(true));
        var searchItem = new JMenuItem("Search…");
        searchItem.addActionListener(e -> openSearch());
        var settingsItem = new JMenuItem("Settings…");
        settingsItem.addActionListener(e -> new SettingsDialog(this, settings, () -> {
            nextActionsPanel.applyColumnVisibility(settings.isShowStatusColumn());
            backlogPanel.applyColumnVisibility(settings.isShowStatusColumn());
        }).setVisible(true));
        var templatesItem = new JMenuItem("Templates…");
        templatesItem.addActionListener(e -> new TemplatesDialog(this, workspace, service).setVisible(true));

        var fileMenu = new JMenu("File");
        fileMenu.add(manageTagsItem);
        fileMenu.add(searchItem);
        fileMenu.add(templatesItem);
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
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        var menuBar = new JMenuBar();
        menuBar.add(fileMenu);

        setJMenuBar(menuBar);
        add(toolbar,   BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);

        navPanel.rebuildSavedViews(workspace.getSavedViews());
    }

    private void onNavSelected(NavigationEntry entry) {
        if (entry.id().startsWith("saved-view:")) {
            var name = entry.id().substring("saved-view:".length());
            workspace.getSavedViews().stream()
                    .filter(sv -> sv.name().equals(name))
                    .findFirst()
                    .ifPresent(sv -> contentArea.setContent(new SavedViewPanel(sv, workspace, service, () -> {
                        rebuildSavedViewsNav();
                        navPanel.selectById("context");
                    })));
            return;
        }
        switch (entry.id()) {
            case "inbox"         -> { contentArea.setContent(inboxPanel);        inboxPanel.refresh(); }
            case "projects"      -> { contentArea.setContent(projectsPanel);     projectsPanel.refresh(); }
            case "next-actions"  -> { contentArea.setContent(nextActionsPanel);  nextActionsPanel.refresh(); }
            case "context"       -> { contentArea.setContent(contextPanel);      contextPanel.refresh(); }
            case "backlog"       -> { contentArea.setContent(backlogPanel);      backlogPanel.refresh(); }
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
                if (isOk) JOptionPane.showMessageDialog(this, msg, push ? "Push" : "Pull", JOptionPane.INFORMATION_MESSAGE);
                else      JOptionPane.showMessageDialog(this, msg, "Sync error", JOptionPane.ERROR_MESSAGE);
            });
        }, "sync-thread").start();
    }

    private void openSearch() {
        contentArea.setContent(searchPanel);
        searchPanel.refresh();
    }

    private void openProjectWorkbench(java.util.UUID projectId) {
        contentArea.setContent(new ProjectWorkbenchPanel(this, workspace, service, projectId, () -> {
            contentArea.setContent(projectsPanel);
            projectsPanel.refresh();
        }));
    }

    private void rebuildSavedViewsNav() {
        navPanel.rebuildSavedViews(workspace.getSavedViews());
    }

    private static JPanel placeholder(String label) {
        var panel = new JPanel(new BorderLayout());
        var text  = new JLabel(label, SwingConstants.CENTER);
        panel.add(text, BorderLayout.CENTER);
        return panel;
    }
}
