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
            new NavigationEntry("raw-tree",     "Raw Tree")
    );

    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final ContentArea contentArea;

    public MainFrame(NamWorkspace workspace, NamWorkspaceService service) {
        this.workspace = workspace;
        this.service   = service;
        this.contentArea = new ContentArea();

        var navPanel  = new NavigationPanel(NAV_ENTRIES, this::onNavSelected);
        var splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navPanel, contentArea);
        splitPane.setDividerLocation(180);
        splitPane.setResizeWeight(0.0);

        add(splitPane, BorderLayout.CENTER);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
    }

    private void onNavSelected(NavigationEntry entry) {
        contentArea.setContent(placeholder(entry.title()));
    }

    private static JPanel placeholder(String label) {
        var panel = new JPanel(new BorderLayout());
        var text  = new JLabel(label, SwingConstants.CENTER);
        panel.add(text, BorderLayout.CENTER);
        return panel;
    }
}
