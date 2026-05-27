package namdesktop.lens;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class BlockedLens {

    public List<BlockedGroup> groups(NamWorkspace workspace) {
        var structural = structuralIds(workspace);
        var grouped    = new LinkedHashMap<UUID, List<NamNode>>();

        workspace.getNodes().values().stream()
                .filter(n -> !structural.contains(n.getId()))
                .filter(n -> !n.isProject())
                .filter(n -> !n.getBlockedBy().isEmpty())
                .forEach(action ->
                        action.getBlockedBy().stream()
                                .map(workspace::getNode)
                                .flatMap(Optional::stream)
                                .filter(blocker -> blocker.getStatus() != NodeStatus.DONE)
                                .forEach(blocker ->
                                        grouped.computeIfAbsent(blocker.getId(), k -> new ArrayList<>())
                                               .add(action)));

        return grouped.entrySet().stream()
                .map(e -> workspace.getNode(e.getKey())
                        .map(blocker -> new BlockedGroup(blocker, List.copyOf(e.getValue())))
                        .orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(g -> g.blocker().getTitle()))
                .toList();
    }

    private static Set<UUID> structuralIds(NamWorkspace workspace) {
        return Stream.of(
                workspace.getRootNodeId(),
                workspace.getInboxNodeId(),
                workspace.getProjectsNodeId(),
                workspace.getNextActionsNodeId()
        ).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
