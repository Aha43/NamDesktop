package namdesktop.service;

import namdesktop.lens.ViewOrderReconciler;
import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.model.ProjectTemplate;
import namdesktop.model.Resource;
import namdesktop.model.TemplateNode;
import namdesktop.persist.WorkspaceRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class NamWorkspaceService {

    public static final String VIEW_NEXT_ACTIONS = "next-actions";
    public static final String VIEW_BACKLOG       = "backlog";

    private final NamWorkspace workspace;
    private final WorkspaceRepository repository;
    private final Path path;

    public NamWorkspaceService(NamWorkspace workspace, WorkspaceRepository repository, Path path) {
        this.workspace = workspace;
        this.repository = repository;
        this.path = path;
    }

    public UUID addChild(UUID parentId, String title) throws IOException {
        var parent = require(parentId);
        var child = new NamNode(UUID.randomUUID(), title);
        if (parentId.equals(workspace.getProjectsNodeId())) child.setProject(true);
        stampCreated(child);
        workspace.getNodes().put(child.getId(), child);
        parent.getChildIds().add(0, child.getId()); // newly added items land first (#386)
        repository.save(path, workspace);
        return child.getId();
    }

    public UUID insertChildBefore(UUID parentId, UUID beforeId, String title) throws IOException {
        var parent = require(parentId);
        var child  = new NamNode(UUID.randomUUID(), title);
        if (parentId.equals(workspace.getProjectsNodeId())) child.setProject(true);
        stampCreated(child);
        workspace.getNodes().put(child.getId(), child);
        var ids   = parent.getChildIds();
        var index = ids.indexOf(beforeId);
        if (index < 0) ids.add(child.getId());
        else           ids.add(index, child.getId());
        repository.save(path, workspace);
        return child.getId();
    }

    public UUID addSubProject(UUID parentId, String title) throws IOException {
        var parent = require(parentId);
        var child = new NamNode(UUID.randomUUID(), title);
        child.setProject(true);
        stampCreated(child);
        workspace.getNodes().put(child.getId(), child);
        parent.getChildIds().add(0, child.getId()); // newly added items land first (#386)
        repository.save(path, workspace);
        return child.getId();
    }

    public void moveChildUp(UUID parentId, UUID childId) throws IOException {
        var ids = require(parentId).getChildIds();
        var idx = ids.indexOf(childId);
        if (idx <= 0) return;
        ids.set(idx, ids.get(idx - 1));
        ids.set(idx - 1, childId);
        repository.save(path, workspace);
    }

    public void moveChildDown(UUID parentId, UUID childId) throws IOException {
        var ids = require(parentId).getChildIds();
        var idx = ids.indexOf(childId);
        if (idx < 0 || idx >= ids.size() - 1) return;
        ids.set(idx, ids.get(idx + 1));
        ids.set(idx + 1, childId);
        repository.save(path, workspace);
    }

    public void moveActionUp(UUID parentId, UUID childId) throws IOException {
        swapWithAdjacentSameKind(parentId, childId, -1);
    }

    public void moveActionDown(UUID parentId, UUID childId) throws IOException {
        swapWithAdjacentSameKind(parentId, childId, 1);
    }

    public void moveProjectUp(UUID parentId, UUID childId) throws IOException {
        swapWithAdjacentSameKind(parentId, childId, -1);
    }

    public void moveProjectDown(UUID parentId, UUID childId) throws IOException {
        swapWithAdjacentSameKind(parentId, childId, 1);
    }

    private void swapWithAdjacentSameKind(UUID parentId, UUID childId, int direction) throws IOException {
        var ids  = require(parentId).getChildIds();
        var node = require(childId);
        var idx  = ids.indexOf(childId);
        for (int i = idx + direction; i >= 0 && i < ids.size(); i += direction) {
            var sibling = workspace.getNode(ids.get(i));
            if (sibling.isPresent() && sibling.get().isProject() == node.isProject()) {
                ids.set(idx, ids.get(i));
                ids.set(i, childId);
                repository.save(path, workspace);
                return;
            }
        }
    }

    public void renameNode(UUID nodeId, String title) throws IOException {
        var node = require(nodeId);
        node.setTitle(title);
        node.setUpdatedAt(LocalDateTime.now());
        repository.save(path, workspace);
    }

    public void setDueDate(UUID nodeId, LocalDate date) throws IOException {
        var node = require(nodeId);
        node.setDueAt(date);
        node.setUpdatedAt(LocalDateTime.now());
        repository.save(path, workspace);
    }

    public void resetWorkspaceToDefault() throws IOException {
        workspace.resetToDefault();
        repository.save(path, workspace);
    }

    public void reloadWorkspace() throws IOException {
        reloadWorkspaceFrom(path);
    }

    public void reloadWorkspaceFrom(Path source) throws IOException {
        var fresh = repository.load(source);
        workspace.setRootNodeId(fresh.getRootNodeId());
        workspace.setInboxNodeId(fresh.getInboxNodeId());
        workspace.setProjectsNodeId(fresh.getProjectsNodeId());
        workspace.setNextActionsNodeId(fresh.getNextActionsNodeId());
        workspace.setNodes(fresh.getNodes());
        workspace.setRegisteredTags(fresh.getRegisteredTags());
        workspace.setSavedViews(fresh.getSavedViews());
        workspace.setMissionControls(fresh.getMissionControls());
        workspace.setTemplates(fresh.getTemplates());
        workspace.setViewOrders(fresh.getViewOrders());
    }

    public void deleteLeaf(UUID nodeId) throws IOException {
        var node = require(nodeId);
        if (!node.getChildIds().isEmpty()) {
            throw new IllegalStateException("Cannot delete a node that has children");
        }
        var parent = findParent(nodeId);
        if (parent != null) {
            parent.getChildIds().remove(nodeId);
        }
        workspace.getNodes().remove(nodeId);
        sweepBlockedBy(nodeId);
        repository.save(path, workspace);
    }

    /**
     * Deletes several leaf nodes in a single save. Nodes that are unknown or still have children
     * are skipped and returned, so the caller can report them; everything else is removed. Saves
     * once, only if at least one node was actually removed.
     */
    public List<UUID> deleteLeaves(List<UUID> nodeIds) throws IOException {
        var skipped = new ArrayList<UUID>();
        var changed = false;
        for (var id : nodeIds) {
            var node = workspace.getNode(id).orElse(null);
            if (node == null || !node.getChildIds().isEmpty()) { skipped.add(id); continue; }
            var parent = findParent(id);
            if (parent != null) parent.getChildIds().remove(id);
            workspace.getNodes().remove(id);
            sweepBlockedBy(id);
            changed = true;
        }
        if (changed) repository.save(path, workspace);
        return List.copyOf(skipped);
    }

    public void deleteRecursive(UUID nodeId) throws IOException {
        require(nodeId);
        var toDelete = workspace.collectSubtree(nodeId);
        var parent = findParent(nodeId);
        if (parent != null) parent.getChildIds().remove(nodeId);
        for (var id : toDelete) {
            workspace.getNodes().remove(id);
            workspace.getViewOrders().values().forEach(order -> order.remove(id));
            sweepBlockedBy(id);
        }
        repository.save(path, workspace);
    }

    private void sweepBlockedBy(UUID deletedId) {
        for (var node : workspace.getNodes().values()) {
            node.getBlockedBy().remove(deletedId);
        }
    }

    public UUID addInboxItem(String title) throws IOException {
        var inboxId = workspace.getInboxNodeId();
        if (inboxId == null) throw new IllegalStateException("Workspace has no inbox node");
        return addChild(inboxId, title);
    }

    public UUID createNextAction(String title) throws IOException {
        return createActionWithStatus(title, NodeStatus.NEXT, List.of());
    }

    public UUID createBacklogAction(String title) throws IOException {
        return createActionWithStatus(title, NodeStatus.BACKLOG, List.of());
    }

    public UUID createNextAction(String title, List<String> tags) throws IOException {
        return createActionWithStatus(title, NodeStatus.NEXT, tags);
    }

    private UUID createActionWithStatus(String title, NodeStatus status, List<String> tags) throws IOException {
        var actionsId = workspace.getNextActionsNodeId();
        if (actionsId == null) throw new IllegalStateException("Workspace has no actions node");
        var node = new NamNode(UUID.randomUUID(), title);
        node.setStatus(status);
        if (!tags.isEmpty()) node.setTags(new java.util.ArrayList<>(tags));
        stampCreated(node);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(actionsId).orElseThrow().getChildIds().add(0, node.getId()); // land first (#386)
        repository.save(path, workspace);
        return node.getId();
    }

    public void updateDescription(UUID nodeId, String description) throws IOException {
        var node = require(nodeId);
        node.setDescription(description);
        node.setUpdatedAt(LocalDateTime.now());
        repository.save(path, workspace);
    }

    public void markDone(UUID nodeId) throws IOException {
        stampStatus(require(nodeId), NodeStatus.DONE);
        repository.save(path, workspace);
    }

    public void markNext(UUID nodeId) throws IOException {
        stampStatus(require(nodeId), NodeStatus.NEXT);
        repository.save(path, workspace);
    }

    public void markBacklog(UUID nodeId) throws IOException {
        stampStatus(require(nodeId), NodeStatus.BACKLOG);
        repository.save(path, workspace);
    }

    /** Sets the same status on several nodes in a single save. Unknown ids are skipped. (#402) */
    public void setStatusForAll(List<UUID> nodeIds, NodeStatus status) throws IOException {
        var changed = false;
        for (var id : nodeIds) {
            var node = workspace.getNode(id).orElse(null);
            if (node == null) continue;
            stampStatus(node, status);
            changed = true;
        }
        if (changed) repository.save(path, workspace);
    }

    /** Archives a project (status → ARCHIVED); it drops out of the active Projects list. (#407) */
    public void archiveProject(UUID projectId) throws IOException {
        var node = require(projectId);
        if (!node.isProject()) throw new IllegalArgumentException("Not a project: " + projectId);
        stampStatus(node, NodeStatus.ARCHIVED);
        repository.save(path, workspace);
    }

    /** Restores an archived project to the active list (status → BACKLOG, the project default). (#407) */
    public void unarchiveProject(UUID projectId) throws IOException {
        var node = require(projectId);
        if (!node.isProject()) throw new IllegalArgumentException("Not a project: " + projectId);
        stampStatus(node, NodeStatus.BACKLOG);
        repository.save(path, workspace);
    }

    /** Adds {@code tag} (normalised) to several nodes, idempotent per node, in a single save. (#402) */
    public void addTagToAll(List<UUID> nodeIds, String tag) throws IOException {
        var normalised = tag != null ? tag.strip().toLowerCase() : "";
        if (normalised.isEmpty()) return;
        var changed = false;
        for (var id : nodeIds) {
            var node = workspace.getNode(id).orElse(null);
            if (node == null) continue;
            if (!node.getTags().contains(normalised)) {
                node.getTags().add(normalised);
                node.setUpdatedAt(LocalDateTime.now());
                changed = true;
            }
        }
        if (changed) repository.save(path, workspace);
    }

    public void updateTags(UUID nodeId, List<String> tags) throws IOException {
        var node = require(nodeId);
        node.setTags(new java.util.ArrayList<>(tags));
        node.setUpdatedAt(LocalDateTime.now());
        repository.save(path, workspace);
    }

    public void touchNode(UUID nodeId) throws IOException {
        var node = workspace.getNode(nodeId).orElse(null);
        if (node == null) return;
        node.setUpdatedAt(LocalDateTime.now());
        repository.save(path, workspace);
    }

    public boolean addPrerequisite(UUID actionId, UUID prereqId) throws IOException {
        require(actionId);
        require(prereqId);
        if (actionId.equals(prereqId)) return false;
        if (wouldCreateCycle(actionId, prereqId)) return false;
        var node = require(actionId);
        if (!node.getBlockedBy().contains(prereqId)) {
            node.getBlockedBy().add(prereqId);
            repository.save(path, workspace);
        }
        return true;
    }

    public void removePrerequisite(UUID actionId, UUID prereqId) throws IOException {
        require(actionId).getBlockedBy().remove(prereqId);
        repository.save(path, workspace);
    }

    public boolean isBlocked(UUID actionId) {
        return require(actionId).getBlockedBy().stream()
                .map(workspace::getNode)
                .flatMap(java.util.Optional::stream)
                .anyMatch(n -> n.getStatus() != NodeStatus.DONE);
    }

    public List<UUID> unblocks(UUID prereqId) {
        return workspace.getNodes().values().stream()
                .filter(n -> n.getBlockedBy().contains(prereqId))
                .map(NamNode::getId)
                .toList();
    }

    public List<String> newlyUnblockedNames(UUID completedId) {
        return unblocks(completedId).stream()
                .filter(id -> !isBlocked(id))
                .map(id -> workspace.getNode(id).map(NamNode::getTitle).orElse("?"))
                .toList();
    }

    public boolean canAddPrerequisite(UUID actionId, UUID prereqId) {
        if (actionId.equals(prereqId)) return false;
        if (workspace.getNode(actionId).isEmpty() || workspace.getNode(prereqId).isEmpty()) return false;
        return !wouldCreateCycle(actionId, prereqId);
    }

    private boolean wouldCreateCycle(UUID actionId, UUID prereqId) {
        // DFS from prereqId following blockedBy links — if we reach actionId it's a cycle
        var visited = new java.util.HashSet<UUID>();
        var stack   = new java.util.ArrayDeque<UUID>();
        stack.push(prereqId);
        while (!stack.isEmpty()) {
            var current = stack.pop();
            if (current.equals(actionId)) return true;
            if (!visited.add(current)) continue;
            workspace.getNode(current).ifPresent(n -> n.getBlockedBy().forEach(stack::push));
        }
        return false;
    }

    public void createMissionControl(String name, List<String> tags) throws IOException {
        var trimmed = name.strip();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Mission Control name must not be blank");
        var mcs = workspace.getMissionControls();
        if (mcs.stream().anyMatch(mc -> mc.name().equals(trimmed))) {
            throw new IllegalArgumentException("A Mission Control named \"" + trimmed + "\" already exists");
        }
        mcs.add(new namdesktop.model.MissionControl(trimmed, List.copyOf(tags)));
        repository.save(path, workspace);
    }

    public void deleteMissionControl(String name) throws IOException {
        var mcs = workspace.getMissionControls();
        if (mcs.removeIf(mc -> mc.name().equals(name))) {
            repository.save(path, workspace);
        }
    }

    public void createSavedView(String name, List<String> tags, boolean nextOnly) throws IOException {
        var trimmed = name.strip();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("View name must not be blank");
        var views = workspace.getSavedViews();
        if (views.stream().anyMatch(v -> v.name().equals(trimmed))) {
            throw new IllegalArgumentException("A view named \"" + trimmed + "\" already exists");
        }
        views.add(new namdesktop.model.SavedView(trimmed, List.copyOf(tags), nextOnly));
        repository.save(path, workspace);
    }

    public void renameSavedView(String oldName, String newName) throws IOException {
        var trimmed = newName.strip();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("View name must not be blank");
        var views = workspace.getSavedViews();
        if (views.stream().anyMatch(v -> v.name().equals(trimmed) && !v.name().equals(oldName))) {
            throw new IllegalArgumentException("A view named \"" + trimmed + "\" already exists");
        }
        for (int i = 0; i < views.size(); i++) {
            if (views.get(i).name().equals(oldName)) {
                var old = views.get(i);
                views.set(i, new namdesktop.model.SavedView(trimmed, old.tags(), old.nextOnly()));
                repository.save(path, workspace);
                return;
            }
        }
    }

    public void deleteSavedView(String name) throws IOException {
        var views = workspace.getSavedViews();
        if (views.removeIf(v -> v.name().equals(name))) {
            repository.save(path, workspace);
        }
    }

    public void registerTag(String tag) throws IOException {
        var normalised = tag.strip().toLowerCase();
        var registered = workspace.getRegisteredTags();
        if (!registered.contains(normalised)) {
            registered.add(normalised);
            repository.save(path, workspace);
        }
    }

    public void addTag(UUID nodeId, String tag) throws IOException {
        var normalised = tag.strip().toLowerCase();
        var node = require(nodeId);
        if (!node.getTags().contains(normalised)) {
            node.getTags().add(normalised);
            node.setUpdatedAt(LocalDateTime.now());
            repository.save(path, workspace);
        }
    }

    public void removeTag(UUID nodeId, String tag) throws IOException {
        var normalised = tag.strip().toLowerCase();
        var node = require(nodeId);
        if (node.getTags().remove(normalised)) {
            node.setUpdatedAt(LocalDateTime.now());
            repository.save(path, workspace);
        }
    }

    public void renameTag(String oldTag, String newTag) throws IOException {
        var changed = false;
        for (var node : workspace.getNodes().values()) {
            var tags = node.getTags();
            if (!tags.contains(oldTag)) continue;
            tags.remove(oldTag);
            if (!tags.contains(newTag)) tags.add(newTag);
            changed = true;
        }
        if (changed) repository.save(path, workspace);
    }

    public void deleteTag(String tag) throws IOException {
        var changed = workspace.getRegisteredTags().remove(tag);
        for (var node : workspace.getNodes().values()) {
            if (node.getTags().remove(tag)) changed = true;
        }
        if (changed) repository.save(path, workspace);
    }

    public void convertInboxItemToProject(UUID id) throws IOException {
        convertFromArea(id, workspace.getInboxNodeId(), workspace.getProjectsNodeId(), "inbox");
    }

    public void convertInboxItemToNextAction(UUID id) throws IOException {
        var node = require(id);
        var inbox = workspace.getNode(workspace.getInboxNodeId())
                .orElseThrow(() -> new IllegalStateException("Inbox area node not found"));
        if (!inbox.getChildIds().remove(id)) {
            throw new IllegalArgumentException("Node is not an inbox item: " + id);
        }
        workspace.getNode(workspace.getNextActionsNodeId())
                .orElseThrow(() -> new IllegalStateException("Actions area node not found"))
                .getChildIds().add(id);
        stampStatus(node, NodeStatus.NEXT);
        repository.save(path, workspace);
    }

    public void convertNextActionToProject(UUID id) throws IOException {
        var node   = require(id);
        var parent = findParent(id);
        if (parent != null && parent.getId().equals(workspace.getNextActionsNodeId())) {
            convertFromArea(id, workspace.getNextActionsNodeId(), workspace.getProjectsNodeId(), "next actions");
        } else {
            node.setProject(true);
            node.setUpdatedAt(LocalDateTime.now());
            repository.save(path, workspace);
        }
    }

    public void convertProjectToAction(UUID id) throws IOException {
        var node = require(id);
        if (!node.getChildIds().isEmpty()) {
            throw new IllegalStateException("Cannot convert a project that has child actions");
        }
        var parent = findParent(id);
        if (parent != null && parent.getId().equals(workspace.getProjectsNodeId())) {
            parent.getChildIds().remove(id);
            workspace.getNode(workspace.getNextActionsNodeId())
                    .orElseThrow(() -> new IllegalStateException("Actions area node not found"))
                    .getChildIds().add(id);
        }
        stampStatus(node, NodeStatus.NEXT);
        repository.save(path, workspace);
    }

    private void convertFromArea(UUID id, UUID sourceId, UUID targetId, String sourceName) throws IOException {
        var node = require(id);
        var source = workspace.getNode(sourceId)
                .orElseThrow(() -> new IllegalStateException("Source area node not found"));
        if (!source.getChildIds().remove(id)) {
            throw new IllegalArgumentException("Node is not a " + sourceName + " item: " + id);
        }
        workspace.getNode(targetId)
                .orElseThrow(() -> new IllegalStateException("Target area node not found"))
                .getChildIds().add(id);
        if (targetId.equals(workspace.getProjectsNodeId())) node.setProject(true);
        node.setUpdatedAt(LocalDateTime.now());
        repository.save(path, workspace);
    }

    public List<NamNode> getViewOrder(String viewKey, List<NamNode> liveItems) {
        var savedOrder = workspace.getViewOrders().getOrDefault(viewKey, List.of());
        return new ViewOrderReconciler().reconcile(savedOrder, liveItems);
    }

    public void moveViewItemUp(String viewKey, UUID nodeId, List<UUID> currentOrder) throws IOException {
        var ids = workspace.getViewOrders().computeIfAbsent(viewKey, k -> new ArrayList<>(currentOrder));
        var idx = ids.indexOf(nodeId);
        if (idx <= 0) return;
        ids.set(idx, ids.get(idx - 1));
        ids.set(idx - 1, nodeId);
        repository.save(path, workspace);
    }

    public void moveViewItemDown(String viewKey, UUID nodeId, List<UUID> currentOrder) throws IOException {
        var ids = workspace.getViewOrders().computeIfAbsent(viewKey, k -> new ArrayList<>(currentOrder));
        var idx = ids.indexOf(nodeId);
        if (idx < 0 || idx >= ids.size() - 1) return;
        ids.set(idx, ids.get(idx + 1));
        ids.set(idx + 1, nodeId);
        repository.save(path, workspace);
    }

    public void addResource(UUID nodeId, Resource resource) throws IOException {
        var node = require(nodeId);
        node.getResources().add(resource);
        node.setUpdatedAt(LocalDateTime.now());
        repository.save(path, workspace);
    }

    public void removeResource(UUID nodeId, int index) throws IOException {
        var node = require(nodeId);
        var resources = node.getResources();
        if (index >= 0 && index < resources.size()) {
            resources.remove(index);
            node.setUpdatedAt(LocalDateTime.now());
            repository.save(path, workspace);
        }
    }

    private NamNode require(UUID nodeId) {
        return workspace.getNode(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));
    }

    public List<ProjectTemplate> getTemplates() {
        return workspace.getTemplates();
    }

    public void saveAsTemplate(String name, UUID nodeId) throws IOException {
        var trimmed = name.strip();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Template name must not be blank");
        var templates = workspace.getTemplates();
        if (templates.stream().anyMatch(t -> t.name().equals(trimmed))) {
            throw new IllegalArgumentException("A template named \"" + trimmed + "\" already exists");
        }
        templates.add(new ProjectTemplate(trimmed, captureChildren(nodeId)));
        repository.save(path, workspace);
    }

    public void deleteTemplate(String name) throws IOException {
        if (workspace.getTemplates().removeIf(t -> t.name().equals(name))) {
            repository.save(path, workspace);
        }
    }

    public void applyTemplate(UUID projectId, ProjectTemplate template) throws IOException {
        require(projectId);
        for (var child : template.children()) cloneTemplateNode(projectId, child);
        repository.save(path, workspace);
    }

    private List<TemplateNode> captureChildren(UUID nodeId) {
        return workspace.getChildren(nodeId).stream()
                .map(child -> new TemplateNode(child.getTitle(), child.isProject(), captureChildren(child.getId())))
                .toList();
    }

    private void cloneTemplateNode(UUID parentId, TemplateNode templateNode) {
        var node = new NamNode(UUID.randomUUID(), templateNode.title());
        node.setProject(templateNode.project());
        stampCreated(node);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(parentId).orElseThrow().getChildIds().add(node.getId());
        for (var child : templateNode.children()) cloneTemplateNode(node.getId(), child);
    }

    public void moveNode(UUID nodeId, UUID newParentId) throws IOException {
        moveNodeBefore(nodeId, newParentId, null);
    }

    /**
     * Moves {@code nodeId} under {@code newParentId}, inserted immediately before
     * {@code anchorId} in the new parent's child order. When {@code anchorId} is null (or not a
     * child of the new parent) the node is appended. Same-parent calls reorder in place.
     */
    public void moveNodeBefore(UUID nodeId, UUID newParentId, UUID anchorId) throws IOException {
        var node = require(nodeId);
        var structuralIds = Set.of(workspace.getRootNodeId(), workspace.getInboxNodeId(),
                workspace.getProjectsNodeId(), workspace.getNextActionsNodeId());
        if (structuralIds.contains(nodeId))
            throw new IllegalArgumentException("Structural nodes cannot be moved.");
        if (!node.isProject() && newParentId.equals(workspace.getProjectsNodeId()))
            throw new IllegalArgumentException("Actions cannot be moved to the top-level project area.");
        var newParent = require(newParentId);
        if (!node.isProject() && !newParent.isProject() && !structuralIds.contains(newParentId))
            throw new IllegalArgumentException("Actions can only be moved into project nodes.");
        if (nodeId.equals(newParentId))
            throw new IllegalArgumentException("A node cannot be its own parent.");
        var subtree = workspace.collectSubtree(nodeId);
        if (subtree.contains(newParentId))
            throw new IllegalArgumentException("Cannot move a node into one of its own descendants.");
        workspace.getNodes().values().forEach(n -> n.getChildIds().remove(nodeId));
        var ids   = newParent.getChildIds();
        var index = anchorId != null ? ids.indexOf(anchorId) : -1;
        if (index < 0) ids.add(nodeId);
        else           ids.add(index, nodeId);
        node.setUpdatedAt(LocalDateTime.now());
        repository.save(path, workspace);
    }

    private NamNode findParent(UUID nodeId) {
        return workspace.getNodes().values().stream()
                .filter(n -> n.getChildIds().contains(nodeId))
                .findFirst()
                .orElse(null);
    }

    private static void stampCreated(NamNode node) {
        var now = LocalDateTime.now();
        node.setCreatedAt(now);
        node.setUpdatedAt(now);
    }

    private static void stampStatus(NamNode node, NodeStatus status) {
        var now = LocalDateTime.now();
        node.setStatus(status);
        node.setUpdatedAt(now);
        node.setStatusChangedAt(now);
    }
}
