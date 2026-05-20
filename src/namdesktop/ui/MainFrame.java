package namdesktop.ui;

import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class MainFrame extends JFrame {

    private static final List<NavigationEntry> NAV_ENTRIES = List.of(
            new NavigationEntry("inbox",        "Inbox"),
            new NavigationEntry("projects",     "Projects"),
            new NavigationEntry("next-actions", "Next Actions"),
            new NavigationEntry("context",      "Context"),
            new NavigationEntry("backlog",      "Backlog"),
            new NavigationEntry("raw-tree",     "Raw Tree")
    );

    private final ContentArea      contentArea;
    private final TreePanel        treePanel;
    private final InboxPanel       inboxPanel;
    private final ProjectsPanel    projectsPanel;
    private final NextActionsPanel nextActionsPanel;
    private final ContextPanel     contextPanel;
    private final BacklogPanel     backlogPanel;

    public MainFrame(NamWorkspace workspace, NamWorkspaceService service) {
        this.contentArea      = new ContentArea();
        this.treePanel        = new TreePanel(workspace, service);
        this.inboxPanel       = new InboxPanel(workspace, service);
        this.projectsPanel    = new ProjectsPanel(workspace, service);
        this.nextActionsPanel = new NextActionsPanel(workspace, service);
        this.contextPanel     = new ContextPanel(workspace, service, () -> {});
        this.backlogPanel     = new BacklogPanel(workspace, service);

        var navPanel  = new NavigationPanel(NAV_ENTRIES, this::onNavSelected);
        var splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navPanel, contentArea);
        splitPane.setDividerLocation(180);
        splitPane.setResizeWeight(0.0);

        var toolbar = new JToolBar();
        toolbar.setFloatable(false);
        var exitButton = new JButton("Exit");
        exitButton.addActionListener(e -> System.exit(0));
        toolbar.add(exitButton);

        var manageTagsItem = new JMenuItem("Manage Tags…");
        manageTagsItem.addActionListener(e ->
                new TagManagementDialog(this, workspace, service).setVisible(true));

        var fileMenu = new JMenu("File");
        fileMenu.add(manageTagsItem);
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
    }

    private void onNavSelected(NavigationEntry entry) {
        switch (entry.id()) {
            case "inbox"    -> { contentArea.setContent(inboxPanel);    inboxPanel.refresh(); }
            case "projects"      -> { contentArea.setContent(projectsPanel);    projectsPanel.refresh(); }
            case "next-actions"  -> { contentArea.setContent(nextActionsPanel); nextActionsPanel.refresh(); }
            case "context"       -> { contentArea.setContent(contextPanel);     contextPanel.refresh(); }
            case "backlog"       -> { contentArea.setContent(backlogPanel);     backlogPanel.refresh(); }
            case "raw-tree"      -> contentArea.setContent(treePanel);
            default         -> contentArea.setContent(placeholder(entry.title()));
        }
    }

    private static JPanel placeholder(String label) {
        var panel = new JPanel(new BorderLayout());
        var text  = new JLabel(label, SwingConstants.CENTER);
        panel.add(text, BorderLayout.CENTER);
        return panel;
    }
}
