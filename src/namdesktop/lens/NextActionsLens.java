package namdesktop.lens;

import namdesktop.model.NodeStatus;
import namdesktop.model.NamWorkspace;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class NextActionsLens {

    public List<NextActionItemRow> items(NamWorkspace workspace) {
        var structural = structuralIds(workspace);
        return workspace.getNodes().values().stream()
                .filter(n -> !structural.contains(n.getId()))
                .filter(n -> !n.isProject())
                .filter(n -> n.getStatus() == NodeStatus.NEXT)
                .map(n -> {
                    var parent = workspace.getParent(n.getId())
                            .filter(p -> !structural.contains(p.getId()))
                            .orElse(null);
                    var isSubProject = parent != null && workspace.getParent(parent.getId())
                            .map(gp -> !gp.getId().equals(workspace.getProjectsNodeId()))
                            .orElse(false);
                    var projectPath = parent != null ? buildProjectPath(workspace, parent.getId(), structural) : null;
                    var ownTags = n.getTags();
                    var inherited = workspace.effectiveTags(n.getId()).stream()
                            .filter(t -> !ownTags.contains(t)).sorted().toList();
                    return new NextActionItemRow(n.getId(), n.getTitle(), n.getStatus(),
                            parent != null ? parent.getTitle() : null,
                            parent != null ? parent.getId() : null,
                            isSubProject, projectPath, List.copyOf(ownTags), inherited);
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
