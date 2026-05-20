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

    // --- addInboxItem ---

    @Test
    void addInboxItem_addsToInboxChildIds() throws IOException {
        var id = service.addInboxItem("Task");
        assertTrue(workspace.getNode(workspace.getInboxNodeId()).orElseThrow()
                .getChildIds().contains(id));
    }

    @Test
    void addInboxItem_savesWorkspace() throws IOException {
        service.addInboxItem("Task");
        assertEquals(1, repository.saveCount);
    }

    @Test
    void addInboxItem_throwsIfInboxMissing() {
        var ws = new NamWorkspace(); // no inboxNodeId set
        var svc = new NamWorkspaceService(ws, repository, Path.of("unused"));
        assertThrows(IllegalStateException.class, () -> svc.addInboxItem("Task"));
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

    // --- markActive ---

    @Test
    void markNext_setsStatusNext() throws IOException {
        service.markDone(rootId);
        service.markNext(rootId);
        assertEquals(NodeStatus.NEXT, workspace.getNode(rootId).orElseThrow().getStatus());
    }

    @Test
    void markNext_savesWorkspace() throws IOException {
        service.markNext(rootId);
        assertEquals(1, repository.saveCount);
    }

    @Test
    void markNext_throwsForUnknownNode() {
        assertThrows(IllegalArgumentException.class,
                () -> service.markNext(UUID.randomUUID()));
    }

    // --- updateDescription ---

    @Test
    void updateDescription_setsDescription() throws IOException {
        service.updateDescription(rootId, "A detailed description");
        assertEquals("A detailed description",
                workspace.getNode(rootId).orElseThrow().getDescription());
    }

    @Test
    void updateDescription_savesWorkspace() throws IOException {
        service.updateDescription(rootId, "Some text");
        assertEquals(1, repository.saveCount);
    }

    @Test
    void updateDescription_throwsForUnknownNode() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateDescription(UUID.randomUUID(), "text"));
    }

    // --- convertNextActionToProject ---

    @Test
    void convertNextActionToProject_movesNodeToProjects() throws IOException {
        var id = service.addInboxItem("Task");
        service.convertInboxItemToNextAction(id);
        repository.saveCount = 0;
        service.convertNextActionToProject(id);
        assertTrue(workspace.getNode(workspace.getProjectsNodeId()).orElseThrow()
                .getChildIds().contains(id));
    }

    @Test
    void convertNextActionToProject_removesFromNextActions() throws IOException {
        var id = service.addInboxItem("Task");
        service.convertInboxItemToNextAction(id);
        service.convertNextActionToProject(id);
        assertFalse(workspace.getNode(workspace.getNextActionsNodeId()).orElseThrow()
                .getChildIds().contains(id));
    }

    @Test
    void convertNextActionToProject_savesWorkspace() throws IOException {
        var id = service.addInboxItem("Task");
        service.convertInboxItemToNextAction(id);
        repository.saveCount = 0;
        service.convertNextActionToProject(id);
        assertEquals(1, repository.saveCount);
    }

    @Test
    void convertNextActionToProject_throwsForUnknownId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.convertNextActionToProject(UUID.randomUUID()));
    }

    @Test
    void convertNextActionToProject_throwsIfNotNextActionsChild() throws IOException {
        var id = service.addInboxItem("Task");
        assertThrows(IllegalArgumentException.class,
                () -> service.convertNextActionToProject(id));
    }

    // --- convertInboxItemToProject ---

    @Test
    void convertInboxItemToProject_movesNodeToProjects() throws IOException {
        var id = service.addInboxItem("Task");
        repository.saveCount = 0;
        service.convertInboxItemToProject(id);
        assertTrue(workspace.getNode(workspace.getProjectsNodeId()).orElseThrow()
                .getChildIds().contains(id));
    }

    @Test
    void convertInboxItemToProject_removesFromInbox() throws IOException {
        var id = service.addInboxItem("Task");
        service.convertInboxItemToProject(id);
        assertFalse(workspace.getNode(workspace.getInboxNodeId()).orElseThrow()
                .getChildIds().contains(id));
    }

    @Test
    void convertInboxItemToProject_savesWorkspace() throws IOException {
        var id = service.addInboxItem("Task");
        repository.saveCount = 0;
        service.convertInboxItemToProject(id);
        assertEquals(1, repository.saveCount);
    }

    @Test
    void convertInboxItemToProject_throwsForUnknownId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.convertInboxItemToProject(UUID.randomUUID()));
    }

    @Test
    void convertInboxItemToProject_throwsIfNotInboxChild() throws IOException {
        var id = service.addChild(rootId, "NotAnInboxItem");
        assertThrows(IllegalArgumentException.class,
                () -> service.convertInboxItemToProject(id));
    }

    // --- convertInboxItemToNextAction ---

    @Test
    void convertInboxItemToNextAction_movesNodeToNextActions() throws IOException {
        var id = service.addInboxItem("Task");
        service.convertInboxItemToNextAction(id);
        assertTrue(workspace.getNode(workspace.getNextActionsNodeId()).orElseThrow()
                .getChildIds().contains(id));
    }

    @Test
    void convertInboxItemToNextAction_removesFromInbox() throws IOException {
        var id = service.addInboxItem("Task");
        service.convertInboxItemToNextAction(id);
        assertFalse(workspace.getNode(workspace.getInboxNodeId()).orElseThrow()
                .getChildIds().contains(id));
    }

    @Test
    void convertInboxItemToNextAction_savesWorkspace() throws IOException {
        var id = service.addInboxItem("Task");
        repository.saveCount = 0;
        service.convertInboxItemToNextAction(id);
        assertEquals(1, repository.saveCount);
    }

    @Test
    void convertInboxItemToNextAction_throwsForUnknownId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.convertInboxItemToNextAction(UUID.randomUUID()));
    }

    @Test
    void convertInboxItemToNextAction_throwsIfNotInboxChild() throws IOException {
        var id = service.addChild(rootId, "NotAnInboxItem");
        assertThrows(IllegalArgumentException.class,
                () -> service.convertInboxItemToNextAction(id));
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
