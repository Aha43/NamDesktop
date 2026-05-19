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

    public void markDone(UUID nodeId) throws IOException {
        require(nodeId).setStatus(NodeStatus.DONE);
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
