package namdesktop.service;

import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.persist.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NamWorkspaceServiceTest {

    private NamWorkspace workspace;
    private InMemoryRepository repository;
    private NamWorkspaceService service;
    private UUID rootId;

    @BeforeEach
    void setUp() {
        workspace = NamWorkspace.createDefault();
        repository = new InMemoryRepository();
        service = new NamWorkspaceService(workspace, repository, Path.of("unused"));
        rootId = workspace.getRootNodeId();
    }

    // --- addChild ---

    @Test
    void addChild_addsNodeToMap() throws IOException {
        var id = service.addChild(rootId, "Child");
        assertTrue(workspace.getNode(id).isPresent());
    }

    @Test
    void addChild_appendsIdToParentChildIds() throws IOException {
        var id = service.addChild(rootId, "Child");
        assertTrue(workspace.getNode(rootId).orElseThrow().getChildIds().contains(id));
    }

    @Test
    void addChild_returnsNewNodeId() throws IOException {
        var id = service.addChild(rootId, "Child");
        assertEquals("Child", workspace.getNode(id).orElseThrow().getTitle());
    }

    @Test
    void addChild_savesWorkspace() throws IOException {
        service.addChild(rootId, "Child");
        assertEquals(1, repository.saveCount);
    }

    @Test
    void addChild_throwsForUnknownParent() {
        assertThrows(IllegalArgumentException.class,
                () -> service.addChild(UUID.randomUUID(), "Child"));
    }

    // --- renameNode ---

    @Test
    void renameNode_updatesTitle() throws IOException {
        service.renameNode(rootId, "Renamed");
        assertEquals("Renamed", workspace.getNode(rootId).orElseThrow().getTitle());
    }

    @Test
    void renameNode_savesWorkspace() throws IOException {
        service.renameNode(rootId, "Renamed");
        assertEquals(1, repository.saveCount);
    }

    @Test
    void renameNode_throwsForUnknownNode() {
        assertThrows(IllegalArgumentException.class,
                () -> service.renameNode(UUID.randomUUID(), "X"));
    }

    // --- deleteLeaf ---

    @Test
    void deleteLeaf_removesNodeFromMap() throws IOException {
        var id = service.addChild(rootId, "Leaf");
        repository.saveCount = 0;
        service.deleteLeaf(id);
        assertTrue(workspace.getNode(id).isEmpty());
    }

    @Test
    void deleteLeaf_removesIdFromParentChildIds() throws IOException {
        var id = service.addChild(rootId, "Leaf");
        service.deleteLeaf(id);
        assertFalse(workspace.getNode(rootId).orElseThrow().getChildIds().contains(id));
    }

    @Test
    void deleteLeaf_savesWorkspace() throws IOException {
        var id = service.addChild(rootId, "Leaf");
        repository.saveCount = 0;
        service.deleteLeaf(id);
        assertEquals(1, repository.saveCount);
    }

    @Test
    void deleteLeaf_throwsForUnknownNode() {
        assertThrows(IllegalArgumentException.class,
                () -> service.deleteLeaf(UUID.randomUUID()));
    }

    @Test
    void deleteLeaf_throwsForNonLeaf() throws IOException {
        service.addChild(rootId, "Child");
        assertThrows(IllegalStateException.class,
                () -> service.deleteLeaf(rootId));
    }

    // --- markDone ---

    @Test
    void markDone_setsStatusDone() throws IOException {
        service.markDone(rootId);
        assertEquals(NodeStatus.DONE, workspace.getNode(rootId).orElseThrow().getStatus());
    }

    @Test
    void markDone_savesWorkspace() throws IOException {
        service.markDone(rootId);
        assertEquals(1, repository.saveCount);
    }

    @Test
    void markDone_throwsForUnknownNode() {
        assertThrows(IllegalArgumentException.class,
                () -> service.markDone(UUID.randomUUID()));
    }

    // --- stub ---

    private static final class InMemoryRepository implements WorkspaceRepository {
        int saveCount = 0;

        @Override
        public NamWorkspace load(Path path) { return NamWorkspace.createDefault(); }

        @Override
        public void save(Path path, NamWorkspace workspace) { saveCount++; }
    }
}
