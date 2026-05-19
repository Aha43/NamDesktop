package namdesktop.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class NamWorkspace {

    private UUID rootNodeId;
    private Map<UUID, NamNode> nodes = new LinkedHashMap<>();

    public NamWorkspace() {}

    public static NamWorkspace createDefault() {
        var workspace = new NamWorkspace();
        var root = new NamNode(UUID.randomUUID(), "NAM");
        workspace.nodes.put(root.getId(), root);
        workspace.rootNodeId = root.getId();
        return workspace;
    }

    public UUID getRootNodeId() { return rootNodeId; }
    public void setRootNodeId(UUID rootNodeId) { this.rootNodeId = rootNodeId; }

    public Map<UUID, NamNode> getNodes() { return nodes; }
    public void setNodes(Map<UUID, NamNode> nodes) { this.nodes = nodes; }

    public Optional<NamNode> getNode(UUID id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public List<NamNode> getChildren(UUID parentId) {
        var parent = nodes.get(parentId);
        if (parent == null) return List.of();
        return parent.getChildIds().stream()
                .map(nodes::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
