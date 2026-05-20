package namdesktop.service;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.persist.WorkspaceRepository;

import java.io.IOException;
import java.nio.file.Path;
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
        workspace.getNodes().put(child.getId(), child);
        parent.getChildIds().add(child.getId());
        repository.save(path, workspace);
        return child.getId();
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

    private void convertFromArea(UUID id, UUID sourceId, UUID targetId, String sourceName) throws IOException {
        require(id);
        var source = workspace.getNode(sourceId)
                .orElseThrow(() -> new IllegalStateException("Source area node not found"));
        if (!source.getChildIds().remove(id)) {
            throw new IllegalArgumentException("Node is not a " + sourceName + " item: " + id);
        }
        workspace.getNode(targetId)
                .orElseThrow(() -> new IllegalStateException("Target area node not found"))
                .getChildIds().add(id);
        repository.save(path, workspace);
    }

    private NamNode require(UUID nodeId) {
        return workspace.getNode(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));
    }

    private NamNode findParent(UUID nodeId) {
        return workspace.getNodes().values().stream()
                .filter(n -> n.getChildIds().contains(nodeId))
                .findFirst()
                .orElse(null);
    }
}
