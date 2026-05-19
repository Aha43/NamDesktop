package namdesktop.ui;

import namdesktop.model.NamWorkspace;

import javax.swing.*;
import java.awt.*;

public final class TreePanel extends JPanel {

    private final JTree tree;
    private final WorkspaceTreeModel model;

    public TreePanel(NamWorkspace workspace) {
        setLayout(new BorderLayout());
        model = new WorkspaceTreeModel(workspace);
        tree = new JTree(model);
        tree.setRootVisible(true);
        tree.expandRow(0);
        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    public JTree getTree() { return tree; }

    public WorkspaceTreeModel getModel() { return model; }
}
