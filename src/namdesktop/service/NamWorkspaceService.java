package namdesktop.service;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.model.ProjectTemplate;
import namdesktop.model.TemplateNode;
import namdesktop.persist.WorkspaceRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class NamWorkspaceService {

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
        workspace.getNodes().put(child.getId(), child);
        parent.getChildIds().add(child.getId());
        repository.save(path, workspace);
        return child.getId();
    }

    public UUID addSubProject(UUID parentId, String title) throws IOException {
        var parent = require(parentId);
        var child = new NamNode(UUID.randomUUID(), title);
        child.setProject(true);
        workspace.getNodes().put(child.getId(), child);
        parent.getChildIds().add(child.getId());
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
        require(nodeId).setTitle(title);
        repository.save(path, workspace);
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
        repository.save(path, workspace);
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
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(actionsId).orElseThrow().getChildIds().add(node.getId());
        repository.save(path, workspace);
        return node.getId();
    }

    public void updateDescription(UUID nodeId, String description) throws IOException {
        require(nodeId).setDescription(description);
        repository.save(path, workspace);
    }

    public void markDone(UUID nodeId) throws IOException {
        require(nodeId).setStatus(NodeStatus.DONE);
        repository.save(path, workspace);
    }

    public void markNext(UUID nodeId) throws IOException {
        require(nodeId).setStatus(NodeStatus.NEXT);
        repository.save(path, workspace);
    }

    public void markBacklog(UUID nodeId) throws IOException {
        require(nodeId).setStatus(NodeStatus.BACKLOG);
        repository.save(path, workspace);
    }

    public void updateTags(UUID nodeId, List<String> tags) throws IOException {
        require(nodeId).setTags(new java.util.ArrayList<>(tags));
        repository.save(path, workspace);
    }

    public void createSavedView(String name, List<String> tags) throws IOException {
        var trimmed = name.strip();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("View name must not be blank");
        var views = workspace.getSavedViews();
        if (views.stream().anyMatch(v -> v.name().equals(trimmed))) {
            throw new IllegalArgumentException("A view named \"" + trimmed + "\" already exists");
        }
        views.add(new namdesktop.model.SavedView(trimmed, List.copyOf(tags)));
        repository.save(path, workspace);
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
        var tags = require(nodeId).getTags();
        if (!tags.contains(normalised)) {
            tags.add(normalised);
            repository.save(path, workspace);
        }
    }

    public void removeTag(UUID nodeId, String tag) throws IOException {
        var normalised = tag.strip().toLowerCase();
        var tags = require(nodeId).getTags();
        if (tags.remove(normalised)) {
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
        require(id);
        var inbox = workspace.getNode(workspace.getInboxNodeId())
                .orElseThrow(() -> new IllegalStateException("Inbox area node not found"));
        if (!inbox.getChildIds().remove(id)) {
            throw new IllegalArgumentException("Node is not an inbox item: " + id);
        }
        workspace.getNode(workspace.getNextActionsNodeId())
                .orElseThrow(() -> new IllegalStateException("Actions area node not found"))
                .getChildIds().add(id);
        require(id).setStatus(NodeStatus.NEXT);
        repository.save(path, workspace);
    }

    public void convertNextActionToProject(UUID id) throws IOException {
        convertFromArea(id, workspace.getNextActionsNodeId(), workspace.getProjectsNodeId(), "next actions");
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
        node.setStatus(NodeStatus.NEXT);
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
        repository.save(path, workspace);
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
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(parentId).orElseThrow().getChildIds().add(node.getId());
        for (var child : templateNode.children()) cloneTemplateNode(node.getId(), child);
    }

    private NamNode findParent(UUID nodeId) {
        return workspace.getNodes().values().stream()
                .filter(n -> n.getChildIds().contains(nodeId))
                .findFirst()
                .orElse(null);
    }
}
