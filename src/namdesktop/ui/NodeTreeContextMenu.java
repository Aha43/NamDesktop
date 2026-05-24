package namdesktop.ui;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public final class NodeTreeContextMenu extends JPopupMenu {

    private final JTree tree;
    private final WorkspaceTreeModel model;
    private final NamWorkspaceService service;
    private final NamWorkspace workspace;
    private final Runnable onDelete;
    private final JMenuItem markDoneItem;
    private final JMenuItem deleteItem;
    private final JMenuItem moveUpItem;
    private final JMenuItem moveDownItem;

    private NamNode targetNode;

    public NodeTreeContextMenu(JTree tree, WorkspaceTreeModel model, NamWorkspaceService service, Runnable onDelete) {
        this.workspace = model.getWorkspace();
        this.tree = tree;
        this.model = model;
        this.service = service;
        this.onDelete = onDelete;

        var addItem    = new JMenuItem("Add child");
        var renameItem = new JMenuItem("Rename");
        markDoneItem   = new JMenuItem("Mark done");
        moveUpItem     = new JMenuItem("Move up");
        moveDownItem   = new JMenuItem("Move down");
        deleteItem     = new JMenuItem("Delete");

        addItem.addActionListener(e    -> addChild());
        renameItem.addActionListener(e -> rename());
        markDoneItem.addActionListener(e -> markDone());
        moveUpItem.addActionListener(e   -> move(-1));
        moveDownItem.addActionListener(e -> move(1));
        deleteItem.addActionListener(e -> delete());

        add(addItem);
        add(renameItem);
        add(markDoneItem);
        addSeparator();
        add(moveUpItem);
        add(moveDownItem);
        addSeparator();
        add(deleteItem);
    }

    public void show(Component invoker, int x, int y, NamNode node) {
        this.targetNode = node;
        markDoneItem.setEnabled(node.getStatus() != NodeStatus.DONE);
        deleteItem.setEnabled(model.getRoot() != node);

        var path = tree.getSelectionPath();
        var isRoot = model.getRoot() == node;
        if (!isRoot && path != null && path.getParentPath() != null) {
            var parentNode   = (NamNode) path.getParentPath().getLastPathComponent();
            var siblingIndex = model.getIndexOfChild(parentNode, node);
            var siblingCount = model.getChildCount(parentNode);
            moveUpItem.setEnabled(siblingIndex > 0);
            moveDownItem.setEnabled(siblingIndex < siblingCount - 1);
        } else {
            moveUpItem.setEnabled(false);
            moveDownItem.setEnabled(false);
        }

        super.show(invoker, x, y);
    }

    private void addChild() {
        var title = JOptionPane.showInputDialog(
                parent(), "Enter node title:", "Add Child", JOptionPane.PLAIN_MESSAGE);
        if (title == null || title.isBlank()) return;
        try {
            var parentPath = tree.getSelectionPath();
            service.addChild(targetNode.getId(), title.strip());
            reload();
            if (parentPath != null) tree.expandPath(parentPath);
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private void rename() {
        var title = (String) JOptionPane.showInputDialog(
                parent(), "Enter new title:", "Rename",
                JOptionPane.PLAIN_MESSAGE, null, null, targetNode.getTitle());
        if (title == null || title.isBlank()) return;
        try {
            service.renameNode(targetNode.getId(), title.strip());
            reload();
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private void markDone() {
        try {
            service.markDone(targetNode.getId());
            reload();
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private void move(int direction) {
        var path = tree.getSelectionPath();
        if (path == null || path.getParentPath() == null) return;
        var parentNode = (NamNode) path.getParentPath().getLastPathComponent();
        try {
            if (direction < 0) service.moveChildUp(parentNode.getId(), targetNode.getId());
            else               service.moveChildDown(parentNode.getId(), targetNode.getId());
            reload();
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private void delete() {
        var subtree  = workspace.collectSubtree(targetNode.getId());
        var projects = (int) subtree.stream().skip(1)
                .map(workspace::getNode).flatMap(java.util.Optional::stream)
                .filter(NamNode::isProject).count();
        var actions  = (int) subtree.stream().skip(1)
                .map(workspace::getNode).flatMap(java.util.Optional::stream)
                .filter(n -> !n.isProject()).count();
        String msg;
        if (projects == 0 && actions == 0) {
            msg = "Delete \"" + targetNode.getTitle() + "\"? This cannot be undone.";
        } else {
            var parts = new java.util.ArrayList<String>();
            if (projects > 0) parts.add(projects + " sub-project" + (projects > 1 ? "s" : ""));
            if (actions  > 0) parts.add(actions  + " action"      + (actions  > 1 ? "s" : ""));
            msg = "Delete \"" + targetNode.getTitle() + "\"? This will also permanently remove "
                    + String.join(" and ", parts) + ".";
        }
        var confirm = JOptionPane.showConfirmDialog(parent(), msg,
                "Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            service.deleteRecursive(targetNode.getId());
            reload();
            onDelete.run();
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private void reload() {
        var expanded = new java.util.ArrayList<javax.swing.tree.TreePath>();
        for (int i = 0; i < tree.getRowCount(); i++) {
            if (tree.isExpanded(i)) expanded.add(tree.getPathForRow(i));
        }
        model.reload();
        expanded.forEach(tree::expandPath);
    }

    private Window parent() {
        return SwingUtilities.getWindowAncestor(tree);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(parent(), message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
