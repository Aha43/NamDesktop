package namdesktop.lens;

import namdesktop.model.NodeStatus;
import namdesktop.model.NamWorkspace;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class BacklogLens {

    public List<BacklogItemRow> items(NamWorkspace workspace) {
        var structural = structuralIds(workspace);
        return workspace.getNodes().values().stream()
                .filter(n -> !structural.contains(n.getId()))
                .filter(n -> !n.isProject())
                .filter(n -> n.getStatus() == NodeStatus.BACKLOG)
                .map(n -> {
                    var parent = workspace.getParent(n.getId()).orElse(null);
                    var isInboxItem = parent != null && parent.getId().equals(workspace.getInboxNodeId());
                    var displayParent = parent != null && !structural.contains(parent.getId()) ? parent : null;
                    var isSubProject = displayParent != null && workspace.getParent(displayParent.getId())
                            .map(gp -> !gp.getId().equals(workspace.getProjectsNodeId()))
                            .orElse(false);
                    var projectPath = displayParent != null ? buildProjectPath(workspace, displayParent.getId(), structural) : null;
                    return new BacklogItemRow(n.getId(), n.getTitle(), n.getStatus(),
                            displayParent != null ? displayParent.getTitle() : null,
                            displayParent != null ? displayParent.getId() : null,
                            isSubProject, projectPath, List.copyOf(n.getTags()), isInboxItem);
                })
                .toList();
    }

    private static String buildProjectPath(NamWorkspace workspace, UUID projectId, Set<UUID> structural) {
        var path = workspace.buildPath(projectId);
        var sb = new StringBuilder();
        for (var node : path) {
            if (structural.contains(node.getId())) continue;
            if (!sb.isEmpty()) sb.append(" > ");
            sb.append(node.getTitle());
        }
        return sb.toString();
    }

    private Set<UUID> structuralIds(NamWorkspace workspace) {
        return Stream.of(
                workspace.getRootNodeId(),
                workspace.getInboxNodeId(),
                workspace.getProjectsNodeId(),
                workspace.getNextActionsNodeId()
        ).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
