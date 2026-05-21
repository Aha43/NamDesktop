package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.ui.UiHelper;
import namdesktop.app.AppSettings;
import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class MainFrame extends JFrame {

    private static List<NavigationEntry> buildNavEntries(boolean devMode) {
        var entries = new java.util.ArrayList<>(List.of(
                new NavigationEntry("inbox",        "Inbox"),
                new NavigationEntry("projects",     "Projects"),
                new NavigationEntry("next-actions", "Next Actions"),
                new NavigationEntry("context",      "Context"),
                new NavigationEntry("backlog",      "Backlog")
        ));
        if (devMode) entries.add(new NavigationEntry("raw-tree", "Raw Tree"));
        return List.copyOf(entries);
    }

    private final NamWorkspace        workspace;
    private final NamWorkspaceService service;
    private final AppSettings         settings;
    private final ContentArea         contentArea;
    private final NavigationPanel  navPanel;
    private final TreePanel        treePanel;
    private final InboxPanel       inboxPanel;
    private final ProjectsPanel    projectsPanel;
    private final NextActionsPanel nextActionsPanel;
    private final ContextPanel     contextPanel;
    private final BacklogPanel     backlogPanel;
    private final SearchPanel      searchPanel;

    public MainFrame(NamWorkspace workspace, NamWorkspaceService service, boolean devMode, AppSettings settings) {
        this.workspace        = workspace;
        this.service          = service;
        this.settings         = settings;
        this.contentArea      = new ContentArea();
        this.treePanel        = new TreePanel(workspace, service);
        this.inboxPanel       = new InboxPanel(workspace, service);
        this.projectsPanel    = new ProjectsPanel(workspace, service);
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
        toolbar.add(Box.createHorizontalGlue());
        var exitButton = new JButton("Exit");
        exitButton.addActionListener(e -> System.exit(0));
        toolbar.add(exitButton);

        var manageTagsItem = new JMenuItem("Manage Tags…");
        manageTagsItem.addActionListener(e ->
                new TagManagementDialog(this, workspace, service).setVisible(true));
        var searchItem = new JMenuItem("Search…");
        searchItem.addActionListener(e -> openSearch());
        var settingsItem = new JMenuItem("Settings…");
        settingsItem.addActionListener(e -> new SettingsDialog(this, settings).setVisible(true));

        var fileMenu = new JMenu("File");
        fileMenu.add(manageTagsItem);
        fileMenu.add(searchItem);
        fileMenu.addSeparator();
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

    private void openSearch() {
        contentArea.setContent(searchPanel);
        searchPanel.refresh();
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
