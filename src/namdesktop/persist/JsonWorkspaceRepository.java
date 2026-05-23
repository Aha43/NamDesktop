package namdesktop.persist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.ProjectTemplate;
import namdesktop.model.SavedView;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

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
        workspace.setProjectsNodeId(file.projectsNodeId);
        workspace.setNextActionsNodeId(file.nextActionsNodeId);
        workspace.setRegisteredTags(file.registeredTags);
        workspace.setSavedViews(file.savedViews);
        workspace.setTemplates(file.templates);
        workspace.setViewOrders(file.viewOrders);
        migrate(workspace, "Inbox",        workspace.getInboxNodeId(),        workspace::setInboxNodeId);
        migrate(workspace, "Projects",     workspace.getProjectsNodeId(),     workspace::setProjectsNodeId);
        migrate(workspace, "Actions",      workspace.getNextActionsNodeId(),  workspace::setNextActionsNodeId);
        return workspace;
    }

    @Override
    public void save(Path path, NamWorkspace workspace) throws IOException {
        Files.createDirectories(path.getParent());
        var file = new WorkspaceFile();
        file.formatVersion      = FORMAT_VERSION;
        file.rootNodeId         = workspace.getRootNodeId();
        file.inboxNodeId        = workspace.getInboxNodeId();
        file.projectsNodeId     = workspace.getProjectsNodeId();
        file.nextActionsNodeId  = workspace.getNextActionsNodeId();
        file.nodes              = workspace.getNodes();
        file.registeredTags     = workspace.getRegisteredTags();
        file.savedViews         = workspace.getSavedViews();
        file.templates          = workspace.getTemplates();
        file.viewOrders         = workspace.getViewOrders();
        mapper.writeValue(path.toFile(), file);
    }

    private static void migrate(NamWorkspace workspace, String title, UUID currentId, Consumer<UUID> setter) {
        if (currentId != null) return;
        var node = new NamNode(UUID.randomUUID(), title);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(workspace.getRootNodeId())
                 .ifPresent(root -> root.getChildIds().add(node.getId()));
        setter.accept(node.getId());
    }

    private static final class WorkspaceFile {
        public int formatVersion;
        public UUID rootNodeId;
        public UUID inboxNodeId;
        public UUID projectsNodeId;
        public UUID nextActionsNodeId;
        public Map<UUID, NamNode> nodes;
        public java.util.List<String> registeredTags;
        public java.util.List<SavedView> savedViews;
        public java.util.List<ProjectTemplate> templates;
        public java.util.Map<String, java.util.List<UUID>> viewOrders;
    }
}
