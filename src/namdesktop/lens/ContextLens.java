package namdesktop.lens;

import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ContextLens {

    public List<ContextItemRow> items(NamWorkspace workspace, Collection<String> requiredTags) {
        return items(workspace, requiredTags, false);
    }

    public List<ContextItemRow> items(NamWorkspace workspace, Collection<String> requiredTags, boolean nextOnly) {
        var structural = structuralIds(workspace);
        return workspace.getNodes().values().stream()
                .filter(n -> !structural.contains(n.getId()))
                .filter(n -> !n.isProject())
                .filter(n -> n.getStatus() != NodeStatus.DONE)
                .filter(n -> !nextOnly || n.getStatus() == NodeStatus.NEXT)
                .filter(n -> workspace.effectiveTags(n.getId()).containsAll(requiredTags))
                .map(n -> {
                    var parent = workspace.getParent(n.getId())
                            .filter(p -> !structural.contains(p.getId()))
                            .orElse(null);
                    var ownTags = n.getTags();
                    var inherited = workspace.effectiveTags(n.getId()).stream()
                            .filter(t -> !ownTags.contains(t))
                            .sorted()
                            .toList();
                    var projectPath = parent != null ? buildProjectPath(workspace, parent.getId(), structural) : null;
                    return new ContextItemRow(n.getId(), n.getTitle(), n.getStatus(),
                            parent != null ? parent.getTitle() : null,
                            projectPath, List.copyOf(ownTags), inherited,
                            !n.getResources().isEmpty(), n.getDueAt());
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
