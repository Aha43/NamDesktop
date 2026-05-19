package namdesktop.ui;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class TreePanel extends JPanel {

    private final JTree tree;
    private final WorkspaceTreeModel model;

    public TreePanel(NamWorkspace workspace, NamWorkspaceService service) {
        setLayout(new BorderLayout());
        model = new WorkspaceTreeModel(workspace);
        tree = new JTree(model);
        tree.setRootVisible(true);
        tree.expandRow(0);

        var contextMenu = new NodeTreeContextMenu(tree, model, service);
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) handlePopup(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) handlePopup(e);
            }
            private void handlePopup(MouseEvent e) {
                var path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                tree.setSelectionPath(path);
                var node = (NamNode) path.getLastPathComponent();
                contextMenu.show(tree, e.getX(), e.getY(), node);
            }
        });

        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    public JTree getTree() { return tree; }

    public WorkspaceTreeModel getModel() { return model; }
}
