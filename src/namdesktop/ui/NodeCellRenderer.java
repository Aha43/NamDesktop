package namdesktop.ui;

import namdesktop.model.NamNode;
import namdesktop.model.NodeStatus;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

public final class NodeCellRenderer extends DefaultTreeCellRenderer {

    @Override
    public Component getTreeCellRendererComponent(
            JTree tree, Object value, boolean selected, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        if (value instanceof NamNode node && node.getStatus() == NodeStatus.DONE) {
            setText("<html><s>" + node.getTitle() + "</s></html>");
            setForeground(selected ? getTextSelectionColor() : Color.GRAY);
        }
        return this;
    }
}
