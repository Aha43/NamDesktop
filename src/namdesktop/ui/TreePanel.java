package namdesktop.ui;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class TreePanel extends JPanel {

    private final NamWorkspace workspace;
    private final JTree tree;
    private final WorkspaceTreeModel model;

    public TreePanel(NamWorkspace workspace, NamWorkspaceService service, Runnable onDelete) {
        this.workspace = workspace;
        setLayout(new BorderLayout());
        model = new WorkspaceTreeModel(workspace);
        tree = new JTree(model) {
            @Override
            public String getToolTipText(MouseEvent e) {
                var path = getPathForLocation(e.getX(), e.getY());
                if (path == null) return null;
                return buildTooltip((NamNode) path.getLastPathComponent());
            }
        };
        tree.setToolTipText("");
        tree.setRootVisible(true);
        tree.setCellRenderer(new NodeCellRenderer());
        tree.expandRow(0);

        var contextMenu = new NodeTreeContextMenu(tree, model, service, onDelete);
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

    private String buildTooltip(NamNode node) {
        var tags = node.getTags();
        var desc = node.getDescription();
        var descLine = (desc != null && !desc.isBlank())
                ? (desc.length() > 50 ? desc.substring(0, 50) + "…" : desc)
                : "—";
        return "<html>" +
                "<b>Type:</b> "     + resolveType(node) + "<br>" +
                "<b>Status:</b> "   + node.getStatus()  + "<br>" +
                "<b>Tags:</b> "     + (tags.isEmpty() ? "—" : String.join(", ", tags)) + "<br>" +
                "<b>ID:</b> "       + node.getId()      + "<br>" +
                "<b>Children:</b> " + workspace.getChildren(node.getId()).size() + "<br>" +
                "<b>Desc:</b> "     + descLine +
                "</html>";
    }

    private String resolveType(NamNode node) {
        var id = node.getId();
        if (id.equals(workspace.getRootNodeId()))        return "Root";
        if (id.equals(workspace.getInboxNodeId()))       return "Inbox";
        if (id.equals(workspace.getProjectsNodeId()))    return "Projects";
        if (id.equals(workspace.getNextActionsNodeId())) return "Next Actions";
        if (node.isProject())                            return "Project";
        return "Action";
    }
}
