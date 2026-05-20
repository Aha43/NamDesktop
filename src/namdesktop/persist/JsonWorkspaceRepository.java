package namdesktop.persist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public final class JsonWorkspaceRepository implements WorkspaceRepository {

    private static final int FORMAT_VERSION = 1;

    private final ObjectMapper mapper;

    public JsonWorkspaceRepository() {
        mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public NamWorkspace load(Path path) throws IOException {
        if (!Files.exists(path)) {
            return NamWorkspace.createDefault();
        }
        var file = mapper.readValue(path.toFile(), WorkspaceFile.class);
        var workspace = new NamWorkspace();
        workspace.setRootNodeId(file.rootNodeId);
        workspace.setNodes(file.nodes);
        workspace.setInboxNodeId(file.inboxNodeId);
        if (workspace.getInboxNodeId() == null) {
            var inbox = new NamNode(UUID.randomUUID(), "Inbox");
            workspace.getNodes().put(inbox.getId(), inbox);
            workspace.getNode(workspace.getRootNodeId())
                     .ifPresent(root -> root.getChildIds().add(0, inbox.getId()));
            workspace.setInboxNodeId(inbox.getId());
        }
        return workspace;
    }

    @Override
    public void save(Path path, NamWorkspace workspace) throws IOException {
        Files.createDirectories(path.getParent());
        var file = new WorkspaceFile();
        file.formatVersion = FORMAT_VERSION;
        file.rootNodeId = workspace.getRootNodeId();
        file.inboxNodeId = workspace.getInboxNodeId();
        file.nodes = workspace.getNodes();
        mapper.writeValue(path.toFile(), file);
    }

    private static final class WorkspaceFile {
        public int formatVersion;
        public UUID rootNodeId;
        public UUID inboxNodeId;
        public Map<UUID, NamNode> nodes;
    }
}
