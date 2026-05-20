package namdesktop.lens;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SearchLens {

    public List<SearchResultRow> search(NamWorkspace ws, String query) {
        if (query == null || query.isBlank()) return List.of();
        var q = query.trim().toLowerCase();
        var structural = structuralIds(ws);

        return ws.getNodes().values().stream()
                .filter(n -> !structural.contains(n.getId()))
                .filter(n -> n.getStatus() == NodeStatus.NEXT || n.getStatus() == NodeStatus.BACKLOG)
                .filter(n -> matches(n, q))
                .map(n -> toRow(ws, n, structural))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(r -> typeOrder(r.type())))
                .toList();
    }

    private boolean matches(NamNode node, String query) {
        if (node.getTitle().toLowerCase().contains(query)) return true;
        return node.getTags().stream().anyMatch(t -> t.toLowerCase().contains(query));
    }

    private SearchResultRow toRow(NamWorkspace ws, NamNode node, Set<UUID> structural) {
        var type = typeOf(ws, node);
        if (type == null) return null;
        var parent = ws.getParent(node.getId())
                .filter(p -> !structural.contains(p.getId()))
                .orElse(null);
        return new SearchResultRow(node.getId(), node.getTitle(), type,
                parent != null ? parent.getTitle() : null, node.getStatus());
    }

    private String typeOf(NamWorkspace ws, NamNode node) {
        var parentId = ws.getParent(node.getId()).map(NamNode::getId).orElse(null);
        if (Objects.equals(parentId, ws.getInboxNodeId()))       return "Inbox";
        if (Objects.equals(parentId, ws.getProjectsNodeId()))    return "Project";
        return switch (node.getStatus()) {
            case NEXT     -> "Action";
            case BACKLOG  -> "Backlog";
            default       -> null;
        };
    }

    private static int typeOrder(String type) {
        return switch (type) {
            case "Inbox"   -> 0;
            case "Action"  -> 1;
            case "Project" -> 2;
            case "Backlog" -> 3;
            default        -> 4;
        };
    }

    private Set<UUID> structuralIds(NamWorkspace ws) {
        return Stream.of(
                ws.getRootNodeId(),
                ws.getInboxNodeId(),
                ws.getProjectsNodeId(),
                ws.getNextActionsNodeId()
        ).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
