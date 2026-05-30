package namdesktop.service;

import namdesktop.model.NamWorkspace;
import namdesktop.model.Resource;
import namdesktop.model.ResourceType;
import namdesktop.persist.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ResourceServiceTest {

    private NamWorkspace workspace;
    private NamWorkspaceService service;

    @BeforeEach
    void setUp() {
        workspace = NamWorkspace.createDefault();
        service   = new NamWorkspaceService(workspace, new NoOpRepository(), Path.of("unused"));
    }

    private static final class NoOpRepository implements WorkspaceRepository {
        @Override public NamWorkspace load(Path p) { return null; }
        @Override public void save(Path p, NamWorkspace w) {}
    }

    @Test
    void addResource_appendsToNode() throws IOException {
        var id  = service.addChild(workspace.getNextActionsNodeId(), "Buy milk");
        var res = new Resource(ResourceType.URI, "https://example.com", "Store link");
        service.addResource(id, res);
        var resources = workspace.getNode(id).orElseThrow().getResources();
        assertEquals(1, resources.size());
        assertEquals(ResourceType.URI, resources.get(0).getType());
        assertEquals("https://example.com", resources.get(0).getValue());
        assertEquals("Store link", resources.get(0).getDescription());
    }

    @Test
    void addResource_multipleResourcesPreserveOrder() throws IOException {
        var id = service.addChild(workspace.getNextActionsNodeId(), "Research");
        service.addResource(id, new Resource(ResourceType.URI,  "https://a.com", null));
        service.addResource(id, new Resource(ResourceType.TEXT, "note",          null));
        var resources = workspace.getNode(id).orElseThrow().getResources();
        assertEquals(2, resources.size());
        assertEquals(ResourceType.URI,  resources.get(0).getType());
        assertEquals(ResourceType.TEXT, resources.get(1).getType());
    }

    @Test
    void removeResource_byIndex_removesCorrectEntry() throws IOException {
        var id = service.addChild(workspace.getNextActionsNodeId(), "Task");
        service.addResource(id, new Resource(ResourceType.URI,  "https://a.com", null));
        service.addResource(id, new Resource(ResourceType.TEXT, "keep me",       null));
        service.removeResource(id, 0);
        var resources = workspace.getNode(id).orElseThrow().getResources();
        assertEquals(1, resources.size());
        assertEquals("keep me", resources.get(0).getValue());
    }

    @Test
    void removeResource_outOfBoundsIndex_isNoOp() throws IOException {
        var id = service.addChild(workspace.getNextActionsNodeId(), "Task");
        service.addResource(id, new Resource(ResourceType.URI, "https://a.com", null));
        service.removeResource(id, 5);
        assertEquals(1, workspace.getNode(id).orElseThrow().getResources().size());
    }

    @Test
    void addResource_nullDescription_isAllowed() throws IOException {
        var id = service.addChild(workspace.getNextActionsNodeId(), "Task");
        service.addResource(id, new Resource(ResourceType.EMAIL, "a@b.com", null));
        assertNull(workspace.getNode(id).orElseThrow().getResources().get(0).getDescription());
    }

    @Test
    void newNode_hasEmptyResourceList() throws IOException {
        var id = service.addChild(workspace.getNextActionsNodeId(), "Task");
        assertTrue(workspace.getNode(id).orElseThrow().getResources().isEmpty());
    }
}
