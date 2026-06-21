package namdesktop.lens;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;

import java.util.UUID;

/**
 * Serializes a project to a paste-friendly Markdown summary (#404, parity with NamWeb #245/#247/#257).
 *
 * <p>The project becomes a heading, its direct actions a checkbox list (`- [x]` done / `- [ ]` open,
 * with a status suffix for anything that is not next/done), and its sub-projects nested headings.
 * When {@code includeSubProjects} is false only the project's own direct actions are emitted —
 * NamWeb's "include only the current project" toggle.
 */
public final class ProjectSummary {

    private ProjectSummary() {}

    /** Markdown summary for {@code projectId}; empty string if the node is unknown. */
    public static String markdown(NamWorkspace workspace, UUID projectId, boolean includeSubProjects) {
        var node = workspace.getNode(projectId).orElse(null);
        if (node == null) return "";
        var sb = new StringBuilder();
        append(workspace, node, includeSubProjects, 1, sb);
        return sb.toString().stripTrailing() + "\n";
    }

    private static void append(NamWorkspace workspace, NamNode project, boolean recurse,
                               int level, StringBuilder sb) {
        sb.append("#".repeat(Math.min(level, 6))).append(' ').append(project.getTitle()).append('\n');
        if (!project.getTags().isEmpty())
            sb.append('_').append(String.join(" ", project.getTags())).append("_\n");
        sb.append('\n');

        var children    = workspace.getChildren(project.getId());
        var actions     = children.stream().filter(n -> !n.isProject()).toList();
        var subProjects = children.stream().filter(NamNode::isProject).toList();

        if (actions.isEmpty() && (!recurse || subProjects.isEmpty()))
            sb.append("_No actions._\n\n");
        if (!actions.isEmpty()) {
            for (var a : actions) sb.append(actionLine(a)).append('\n');
            sb.append('\n');
        }

        if (recurse)
            for (var sub : subProjects) append(workspace, sub, true, level + 1, sb);
    }

    private static String actionLine(NamNode action) {
        var status = action.getStatus();
        var box    = status == NodeStatus.DONE ? "[x]" : "[ ]";
        var suffix = (status == NodeStatus.DONE || status == NodeStatus.NEXT)
                ? "" : " _(" + status.name().toLowerCase() + ")_";
        return "- " + box + ' ' + action.getTitle() + suffix;
    }
}
