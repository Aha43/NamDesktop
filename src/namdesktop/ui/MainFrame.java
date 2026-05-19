package namdesktop.ui;

import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;

public final class MainFrame extends JFrame {

    public MainFrame(NamWorkspace workspace, NamWorkspaceService service) {
        var treePanel = new TreePanel(workspace, service);
        var centrePanel = new JPanel();

        var splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel, centrePanel);
        splitPane.setDividerLocation(220);
        splitPane.setResizeWeight(0.0);

        add(splitPane, BorderLayout.CENTER);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
    }
}
