package namdesktop.ui;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

public final class WorkspaceTreeModel implements TreeModel {

    private final NamWorkspace workspace;
    private final List<TreeModelListener> listeners = new ArrayList<>();

    public WorkspaceTreeModel(NamWorkspace workspace) {
        this.workspace = workspace;
    }

    @Override
    public Object getRoot() {
        return workspace.getNode(workspace.getRootNodeId()).orElseThrow();
    }

    @Override
    public Object getChild(Object parent, int index) {
        return workspace.getChildren(((NamNode) parent).getId()).get(index);
    }

    @Override
    public int getChildCount(Object parent) {
        return workspace.getChildren(((NamNode) parent).getId()).size();
    }

    @Override
    public boolean isLeaf(Object node) {
        return getChildCount(node) == 0;
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        return workspace.getChildren(((NamNode) parent).getId()).indexOf(child);
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {}

    @Override
    public void addTreeModelListener(TreeModelListener l) { listeners.add(l); }

    @Override
    public void removeTreeModelListener(TreeModelListener l) { listeners.remove(l); }

    public void reload() {
        var event = new TreeModelEvent(this, new Object[]{ getRoot() });
        listeners.forEach(l -> l.treeStructureChanged(event));
    }
}
