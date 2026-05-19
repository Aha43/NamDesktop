package namdesktop.ui;

import namdesktop.model.NamNode;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public final class NodeTreeContextMenu extends JPopupMenu {

    private final JTree tree;
    private final WorkspaceTreeModel model;
    private final NamWorkspaceService service;
    private final JMenuItem deleteItem;

    private NamNode targetNode;

    public NodeTreeContextMenu(JTree tree, WorkspaceTreeModel model, NamWorkspaceService service) {
        this.tree = tree;
        this.model = model;
        this.service = service;

        var addItem = new JMenuItem("Add child");
        var renameItem = new JMenuItem("Rename");
        deleteItem = new JMenuItem("Delete");

        addItem.addActionListener(e -> addChild());
        renameItem.addActionListener(e -> rename());
        deleteItem.addActionListener(e -> delete());

        add(addItem);
        add(renameItem);
        addSeparator();
        add(deleteItem);
    }

    public void show(Component invoker, int x, int y, NamNode node) {
        this.targetNode = node;
        deleteItem.setEnabled(model.getRoot() != node);
        super.show(invoker, x, y);
    }

    private void addChild() {
        var title = JOptionPane.showInputDialog(
                parent(), "Enter node title:", "Add Child", JOptionPane.PLAIN_MESSAGE);
        if (title == null || title.isBlank()) return;
        try {
            service.addChild(targetNode.getId(), title.strip());
            reload();
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

    private void delete() {
        try {
            service.deleteLeaf(targetNode.getId());
            reload();
        } catch (IllegalStateException e) {
            showError(e.getMessage());
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private void reload() {
        model.reload();
        tree.expandRow(0);
    }

    private Window parent() {
        return SwingUtilities.getWindowAncestor(tree);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(parent(), message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
