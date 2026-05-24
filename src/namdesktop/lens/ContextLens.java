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
                    return new ContextItemRow(n.getId(), n.getTitle(), n.getStatus(),
                            parent != null ? parent.getTitle() : null,
                            List.copyOf(ownTags), inherited);
                })
                .toList();
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
