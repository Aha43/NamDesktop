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
                .filter(n -> n.getStatus() == NodeStatus.NEXT)
                .map(n -> {
                    var parent = workspace.getParent(n.getId())
                            .filter(p -> !structural.contains(p.getId()))
                            .orElse(null);
                    return new NextActionItemRow(n.getId(), n.getTitle(), n.getStatus(),
                            parent != null ? parent.getTitle() : null, List.copyOf(n.getTags()));
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
