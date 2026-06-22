package namdesktop.model;

import java.util.ArrayList;
import java.util.ArrayDeque;
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
    private List<String> registeredTags = new ArrayList<>();
    private List<SavedView> savedViews = new ArrayList<>();
    private List<MissionControl> missionControls = new ArrayList<>();
    private List<ProjectTemplate> templates = new ArrayList<>();
    private Map<String, List<UUID>> viewOrders = new LinkedHashMap<>();
    // Document-level fields from a newer/other app (e.g. NamWeb) that this version doesn't model;
    // carried so a save or cloud push doesn't drop them (#416).
    private Map<String, Object> unknownFields = new LinkedHashMap<>();

    public NamWorkspace() {}

    public void resetToDefault() {
        var fresh = createDefault();
        this.rootNodeId        = fresh.rootNodeId;
        this.inboxNodeId       = fresh.inboxNodeId;
        this.projectsNodeId    = fresh.projectsNodeId;
        this.nextActionsNodeId = fresh.nextActionsNodeId;
        this.nodes             = fresh.nodes;
        this.registeredTags    = fresh.registeredTags;
        this.savedViews        = fresh.savedViews;
        this.missionControls   = fresh.missionControls;
        this.templates         = fresh.templates;
        this.viewOrders        = fresh.viewOrders;
    }

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

    public java.util.Set<String> effectiveTags(UUID nodeId) {
        var result = new java.util.HashSet<String>();
        var current = getNode(nodeId);
        if (current.isEmpty()) return result;
        result.addAll(current.get().getTags());
        var ancestor = getParent(nodeId);
        while (ancestor.isPresent()) {
            var a = ancestor.get();
            if (a.isProject()) result.addAll(a.getTags());
            ancestor = getParent(a.getId());
        }
        return result;
    }

    public List<String> allTags() {
        return java.util.stream.Stream.concat(
                        registeredTags.stream(),
                        nodes.values().stream().flatMap(n -> n.getTags().stream()))
                .distinct()
                .sorted()
                .toList();
    }

    public List<String> getRegisteredTags() { return registeredTags; }
    public void setRegisteredTags(List<String> tags) { this.registeredTags = tags != null ? tags : new ArrayList<>(); }

    public List<SavedView> getSavedViews() { return savedViews; }
    public void setSavedViews(List<SavedView> views) { this.savedViews = views != null ? views : new ArrayList<>(); }

    public List<MissionControl> getMissionControls() { return missionControls; }
    public void setMissionControls(List<MissionControl> mcs) { this.missionControls = mcs != null ? mcs : new ArrayList<>(); }

    public List<ProjectTemplate> getTemplates() { return templates; }
    public void setTemplates(List<ProjectTemplate> templates) { this.templates = templates != null ? templates : new ArrayList<>(); }

    public Map<String, List<UUID>> getViewOrders() { return viewOrders; }
    public void setViewOrders(Map<String, List<UUID>> viewOrders) { this.viewOrders = viewOrders != null ? viewOrders : new LinkedHashMap<>(); }

    public Map<String, Object> getUnknownFields() { return unknownFields; }
    public void setUnknownFields(Map<String, Object> unknownFields) { this.unknownFields = unknownFields != null ? unknownFields : new LinkedHashMap<>(); }

    public List<UUID> collectSubtree(UUID rootId) {
        var result = new ArrayList<UUID>();
        var queue  = new ArrayDeque<UUID>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            var id = queue.poll();
            result.add(id);
            var node = nodes.get(id);
            if (node != null) queue.addAll(node.getChildIds());
        }
        return List.copyOf(result);
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
