package namdesktop.ui;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class MoveToDialog {

    private record Entry(UUID id, String label) {
        @Override public String toString() { return label; }
    }

    /**
     * Shows a project-picker dialog and returns the chosen parent UUID, or null if cancelled.
     *
     * @param escapeLabel  label for an escape-hatch entry prepended to the list (e.g. "(Top level)"
     *                     or "(Free action)"), or null for no escape hatch
     * @param escapeTarget UUID for the escape-hatch entry (e.g. projectsNodeId or nextActionsNodeId)
     */
    public static UUID pickProject(Window parent, UUID nodeId, NamWorkspace workspace,
                                   String escapeLabel, UUID escapeTarget) {
        var excluded = Set.copyOf(workspace.collectSubtree(nodeId));
        var entries  = new ArrayList<Entry>();

        if (escapeLabel != null)
            entries.add(new Entry(escapeTarget, escapeLabel));

        collectProjects(workspace, workspace.getProjectsNodeId(), excluded, "", entries);

        boolean onlyEscape = escapeLabel != null && entries.size() == 1;
        if (entries.isEmpty() || onlyEscape) {
            JOptionPane.showMessageDialog(parent, "No other projects available to move to.",
                    "Move to…", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }

        var list = new JList<>(entries.toArray(Entry[]::new));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setVisibleRowCount(Math.min(entries.size(), 12));
        var scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(380, Math.min(entries.size(), 12) * 22 + 4));

        int result = JOptionPane.showConfirmDialog(parent, scroll, "Move to…",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return null;
        var selected = list.getSelectedValue();
        return selected == null ? null : selected.id();
    }

    private static void collectProjects(NamWorkspace ws, UUID parentId, Set<UUID> excluded,
                                        String prefix, List<Entry> out) {
        ws.getChildren(parentId).stream()
                .filter(NamNode::isProject)
                .filter(n -> !excluded.contains(n.getId()))
                .forEach(n -> {
                    var label = prefix.isEmpty() ? n.getTitle() : prefix + " > " + n.getTitle();
                    out.add(new Entry(n.getId(), label));
                    collectProjects(ws, n.getId(), excluded, label, out);
                });
    }
}
