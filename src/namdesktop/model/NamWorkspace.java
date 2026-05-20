package namdesktop.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class NamWorkspace {

    private UUID rootNodeId;
    private UUID inboxNodeId;
    private UUID projectsNodeId;
    private UUID nextActionsNodeId;
    private Map<UUID, NamNode> nodes = new LinkedHashMap<>();

    public NamWorkspace() {}

    public static NamWorkspace createDefault() {
        var workspace    = new NamWorkspace();
        var root         = new NamNode(UUID.randomUUID(), "NAM");
        var inbox        = new NamNode(UUID.randomUUID(), "Inbox");
        var projects     = new NamNode(UUID.randomUUID(), "Projects");
        var nextActions  = new NamNode(UUID.randomUUID(), "Actions");
        workspace.nodes.put(root.getId(),        root);
        workspace.nodes.put(inbox.getId(),       inbox);
        workspace.nodes.put(projects.getId(),    projects);
        workspace.nodes.put(nextActions.getId(), nextActions);
        root.getChildIds().add(inbox.getId());
        root.getChildIds().add(projects.getId());
        root.getChildIds().add(nextActions.getId());
        workspace.rootNodeId       = root.getId();
        workspace.inboxNodeId      = inbox.getId();
        workspace.projectsNodeId   = projects.getId();
        workspace.nextActionsNodeId = nextActions.getId();
        return workspace;
    }

    public UUID getRootNodeId() { return rootNodeId; }
    public void setRootNodeId(UUID rootNodeId) { this.rootNodeId = rootNodeId; }

    public UUID getInboxNodeId() { return inboxNodeId; }
    public void setInboxNodeId(UUID inboxNodeId) { this.inboxNodeId = inboxNodeId; }

    public UUID getProjectsNodeId() { return projectsNodeId; }
    public void setProjectsNodeId(UUID projectsNodeId) { this.projectsNodeId = projectsNodeId; }

    public UUID getNextActionsNodeId() { return nextActionsNodeId; }
    public void setNextActionsNodeId(UUID nextActionsNodeId) { this.nextActionsNodeId = nextActionsNodeId; }

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

    public Optional<NamNode> getParent(UUID childId) {
        return nodes.values().stream()
                .filter(n -> n.getChildIds().contains(childId))
                .findFirst();
    }

    public List<String> allTags() {
        return nodes.values().stream()
                .flatMap(n -> n.getTags().stream())
                .distinct()
                .sorted()
                .toList();
    }

    public List<NamNode> buildPath(UUID nodeId) {
        var path = new ArrayList<NamNode>();
        var current = getNode(nodeId);
        while (current.isPresent()) {
            path.add(0, current.get());
            current = getParent(current.get().getId());
        }
        return List.copyOf(path);
    }
}
