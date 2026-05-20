package namdesktop.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class NamWorkspace {

    private UUID rootNodeId;
    private UUID inboxNodeId;
    private Map<UUID, NamNode> nodes = new LinkedHashMap<>();

    public NamWorkspace() {}

    public static NamWorkspace createDefault() {
        var workspace = new NamWorkspace();
        var root  = new NamNode(UUID.randomUUID(), "NAM");
        var inbox = new NamNode(UUID.randomUUID(), "Inbox");
        workspace.nodes.put(root.getId(),  root);
        workspace.nodes.put(inbox.getId(), inbox);
        root.getChildIds().add(inbox.getId());
        workspace.rootNodeId  = root.getId();
        workspace.inboxNodeId = inbox.getId();
        return workspace;
    }

    public UUID getRootNodeId() { return rootNodeId; }
    public void setRootNodeId(UUID rootNodeId) { this.rootNodeId = rootNodeId; }

    public UUID getInboxNodeId() { return inboxNodeId; }
    public void setInboxNodeId(UUID inboxNodeId) { this.inboxNodeId = inboxNodeId; }

    public Map<UUID, NamNode> getNodes() { return nodes; }
    public void setNodes(Map<UUID, NamNode> nodes) { this.nodes = nodes; }

    public List<NamNode> getInboxItems() {
        if (inboxNodeId == null) return List.of();
        return getChildren(inboxNodeId);
    }

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
