package namdesktop.service;

import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.persist.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
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

    // --- moveChildUp / moveChildDown ---

    @Test
    void moveChildUp_swapsWithPreviousSibling() throws IOException {
        var a = service.addChild(rootId, "A");
        var b = service.addChild(rootId, "B");
        service.moveChildUp(rootId, b);
        var ids = workspace.getNode(rootId).orElseThrow().getChildIds();
        assertTrue(ids.indexOf(b) < ids.indexOf(a));
    }

    @Test
    void moveChildDown_swapsWithNextSibling() throws IOException {
        var a = service.addChild(rootId, "A");
        var b = service.addChild(rootId, "B");
        service.moveChildDown(rootId, a);
        var ids = workspace.getNode(rootId).orElseThrow().getChildIds();
        assertTrue(ids.indexOf(b) < ids.indexOf(a));
    }

    @Test
    void moveChildUp_isNoOpForFirstChild() throws IOException {
        var parentId = service.addChild(rootId, "Parent");
        var a = service.addChild(parentId, "A");
        service.addChild(parentId, "B");
        var before = List.copyOf(workspace.getNode(parentId).orElseThrow().getChildIds());
        service.moveChildUp(parentId, a);
        assertEquals(before, workspace.getNode(parentId).orElseThrow().getChildIds());
    }

    @Test
    void moveChildDown_isNoOpForLastChild() throws IOException {
        var parentId = service.addChild(rootId, "Parent");
        service.addChild(parentId, "A");
        var b = service.addChild(parentId, "B");
        var before = List.copyOf(workspace.getNode(parentId).orElseThrow().getChildIds());
        service.moveChildDown(parentId, b);
        assertEquals(before, workspace.getNode(parentId).orElseThrow().getChildIds());
    }

    @Test
    void moveChildUp_savesWorkspace() throws IOException {
        service.addChild(rootId, "A");
        var b = service.addChild(rootId, "B");
        repository.saveCount = 0;
        service.moveChildUp(rootId, b);
        assertEquals(1, repository.saveCount);
    }

    @Test
    void moveChildDown_savesWorkspace() throws IOException {
        var a = service.addChild(rootId, "A");
        service.addChild(rootId, "B");
        repository.saveCount = 0;
        service.moveChildDown(rootId, a);
        assertEquals(1, repository.saveCount);
    }

    // --- moveActionUp / moveActionDown ---

    @Test
    void moveActionUp_skipsOverProjectSiblings() throws IOException {
        var parentId = service.addChild(rootId, "Parent");
        var sub      = service.addSubProject(parentId, "Sub");
        var a        = service.addChild(parentId, "A");
        var b        = service.addChild(parentId, "B");
        // childIds: [sub, a, b] — moving a up should skip sub and stay put (already first action)
        service.moveActionUp(parentId, a);
        var ids = workspace.getNode(parentId).orElseThrow().getChildIds();
        assertTrue(ids.indexOf(sub) < ids.indexOf(a));
        assertTrue(ids.indexOf(a) < ids.indexOf(b));
    }

    @Test
    void moveActionDown_swapsWithNextActionSkippingProjects() throws IOException {
        var parentId = service.addChild(rootId, "Parent");
        var a        = service.addChild(parentId, "A");
        var sub      = service.addSubProject(parentId, "Sub");
        var b        = service.addChild(parentId, "B");
        // childIds: [a, sub, b] — moving a down should skip sub and swap with b
        service.moveActionDown(parentId, a);
        var ids = workspace.getNode(parentId).orElseThrow().getChildIds();
        assertTrue(ids.indexOf(b) < ids.indexOf(a));
    }

    @Test
    void moveActionUp_isNoOpWhenFirstAmongActions() throws IOException {
        var parentId = service.addChild(rootId, "Parent");
        var sub      = service.addSubProject(parentId, "Sub");
        var a        = service.addChild(parentId, "A");
        service.addChild(parentId, "B");
        var before = List.copyOf(workspace.getNode(parentId).orElseThrow().getChildIds());
        service.moveActionUp(parentId, a);
        assertEquals(before, workspace.getNode(parentId).orElseThrow().getChildIds());
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

    // --- markBacklog ---

    @Test
    void markBacklog_setsStatusBacklog() throws IOException {
        service.markNext(rootId);
        service.markBacklog(rootId);
        assertEquals(NodeStatus.BACKLOG, workspace.getNode(rootId).orElseThrow().getStatus());
    }

    @Test
    void markBacklog_savesWorkspace() throws IOException {
        service.markBacklog(rootId);
        assertEquals(1, repository.saveCount);
    }

    @Test
    void markBacklog_throwsForUnknownNode() {
        assertThrows(IllegalArgumentException.class,
                () -> service.markBacklog(UUID.randomUUID()));
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
    void convertInboxItemToNextAction_setsStatusNext() throws IOException {
        var id = service.addInboxItem("Task");
        service.convertInboxItemToNextAction(id);
        assertEquals(NodeStatus.NEXT, workspace.getNode(id).orElseThrow().getStatus());
    }

    @Test
    void convertInboxItemToNextAction_throwsIfNotInboxChild() throws IOException {
        var id = service.addChild(rootId, "NotAnInboxItem");
        assertThrows(IllegalArgumentException.class,
                () -> service.convertInboxItemToNextAction(id));
    }

    // --- createSavedView / deleteSavedView ---

    @Test
    void createSavedView_addsView() throws IOException {
        service.createSavedView("My view", List.of("@computer"));
        assertEquals(1, workspace.getSavedViews().size());
        assertEquals("My view", workspace.getSavedViews().get(0).name());
        assertEquals(List.of("@computer"), workspace.getSavedViews().get(0).tags());
    }

    @Test
    void createSavedView_savesWorkspace() throws IOException {
        service.createSavedView("My view", List.of("@computer"));
        assertEquals(1, repository.saveCount);
    }

    @Test
    void createSavedView_throwsOnBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createSavedView("  ", List.of("@computer")));
    }

    @Test
    void createSavedView_throwsOnDuplicateName() throws IOException {
        service.createSavedView("My view", List.of("@computer"));
        assertThrows(IllegalArgumentException.class,
                () -> service.createSavedView("My view", List.of("@home")));
    }

    @Test
    void deleteSavedView_removesView() throws IOException {
        service.createSavedView("My view", List.of("@computer"));
        repository.saveCount = 0;
        service.deleteSavedView("My view");
        assertTrue(workspace.getSavedViews().isEmpty());
        assertEquals(1, repository.saveCount);
    }

    @Test
    void deleteSavedView_isNoOpWhenNotFound() throws IOException {
        service.deleteSavedView("nonexistent");
        assertEquals(0, repository.saveCount);
    }

    // --- addTag / removeTag / updateTags ---

    @Test
    void addTag_addsTagToNode() throws IOException {
        service.addTag(rootId, "@computer");
        assertTrue(workspace.getNode(rootId).orElseThrow().getTags().contains("@computer"));
    }

    @Test
    void addTag_normalisesToLowercase() throws IOException {
        service.addTag(rootId, "@Computer");
        assertTrue(workspace.getNode(rootId).orElseThrow().getTags().contains("@computer"));
    }

    @Test
    void addTag_ignoresDuplicate() throws IOException {
        service.addTag(rootId, "@home");
        repository.saveCount = 0;
        service.addTag(rootId, "@home");
        assertEquals(0, repository.saveCount);
        assertEquals(1, workspace.getNode(rootId).orElseThrow().getTags().size());
    }

    @Test
    void addTag_savesWorkspace() throws IOException {
        service.addTag(rootId, "@home");
        assertEquals(1, repository.saveCount);
    }

    @Test
    void addTag_throwsForUnknownNode() {
        assertThrows(IllegalArgumentException.class,
                () -> service.addTag(UUID.randomUUID(), "@home"));
    }

    @Test
    void removeTag_removesExistingTag() throws IOException {
        service.addTag(rootId, "@home");
        repository.saveCount = 0;
        service.removeTag(rootId, "@home");
        assertFalse(workspace.getNode(rootId).orElseThrow().getTags().contains("@home"));
        assertEquals(1, repository.saveCount);
    }

    @Test
    void removeTag_isNoOpForAbsentTag() throws IOException {
        service.removeTag(rootId, "@home");
        assertEquals(0, repository.saveCount);
    }

    @Test
    void updateTags_replacesTags() throws IOException {
        service.addTag(rootId, "@old");
        repository.saveCount = 0;
        service.updateTags(rootId, List.of("@computer", "@home"));
        var tags = workspace.getNode(rootId).orElseThrow().getTags();
        assertEquals(List.of("@computer", "@home"), tags);
        assertEquals(1, repository.saveCount);
    }

    // --- renameTag ---

    @Test
    void renameTag_updatesAllCarryingNodes() throws IOException {
        var id1 = service.addChild(rootId, "A");
        var id2 = service.addChild(rootId, "B");
        service.addTag(id1, "@old");
        service.addTag(id2, "@old");
        repository.saveCount = 0;
        service.renameTag("@old", "@new");
        assertTrue(workspace.getNode(id1).orElseThrow().getTags().contains("@new"));
        assertTrue(workspace.getNode(id2).orElseThrow().getTags().contains("@new"));
        assertFalse(workspace.getNode(id1).orElseThrow().getTags().contains("@old"));
    }

    @Test
    void renameTag_savesOnce() throws IOException {
        var id = service.addChild(rootId, "A");
        service.addTag(id, "@old");
        repository.saveCount = 0;
        service.renameTag("@old", "@new");
        assertEquals(1, repository.saveCount);
    }

    @Test
    void renameTag_doesNotSaveWhenNoNodeCarriesTag() throws IOException {
        service.renameTag("@nonexistent", "@new");
        assertEquals(0, repository.saveCount);
    }

    @Test
    void renameTag_deduplicatesIfTargetAlreadyPresent() throws IOException {
        var id = service.addChild(rootId, "A");
        service.addTag(id, "@old");
        service.addTag(id, "@new");
        service.renameTag("@old", "@new");
        assertEquals(1, workspace.getNode(id).orElseThrow().getTags().stream()
                .filter(t -> t.equals("@new")).count());
    }

    // --- deleteTag ---

    @Test
    void deleteTag_removesFromAllCarryingNodes() throws IOException {
        var id1 = service.addChild(rootId, "A");
        var id2 = service.addChild(rootId, "B");
        service.addTag(id1, "@bye");
        service.addTag(id2, "@bye");
        repository.saveCount = 0;
        service.deleteTag("@bye");
        assertFalse(workspace.getNode(id1).orElseThrow().getTags().contains("@bye"));
        assertFalse(workspace.getNode(id2).orElseThrow().getTags().contains("@bye"));
        assertEquals(1, repository.saveCount);
    }

    @Test
    void deleteTag_removesRegisteredTagWithNoNodeUsage() throws IOException {
        service.registerTag("@unused");
        repository.saveCount = 0;
        service.deleteTag("@unused");
        assertFalse(workspace.getRegisteredTags().contains("@unused"));
        assertEquals(1, repository.saveCount);
    }

    @Test
    void deleteTag_isNoOpWhenTagNotInUse() throws IOException {
        service.deleteTag("@nonexistent");
        assertEquals(0, repository.saveCount);
    }

    // --- convertProjectToAction ---

    @Test
    void convertProjectToAction_topLevel_movesToNextActions() throws IOException {
        var id = service.addInboxItem("Task");
        service.convertInboxItemToProject(id);
        repository.saveCount = 0;
        service.convertProjectToAction(id);
        assertTrue(workspace.getNode(workspace.getNextActionsNodeId()).orElseThrow()
                .getChildIds().contains(id));
    }

    @Test
    void convertProjectToAction_topLevel_removesFromProjects() throws IOException {
        var id = service.addInboxItem("Task");
        service.convertInboxItemToProject(id);
        service.convertProjectToAction(id);
        assertFalse(workspace.getNode(workspace.getProjectsNodeId()).orElseThrow()
                .getChildIds().contains(id));
    }

    @Test
    void convertProjectToAction_topLevel_setsStatusNext() throws IOException {
        var id = service.addInboxItem("Task");
        service.convertInboxItemToProject(id);
        service.convertProjectToAction(id);
        assertEquals(NodeStatus.NEXT, workspace.getNode(id).orElseThrow().getStatus());
    }

    @Test
    void convertProjectToAction_subProject_staysUnderParent() throws IOException {
        var projectId = service.addInboxItem("Project");
        service.convertInboxItemToProject(projectId);
        var subId = service.addChild(projectId, "Sub");
        service.convertProjectToAction(subId);
        assertTrue(workspace.getNode(projectId).orElseThrow().getChildIds().contains(subId));
        assertFalse(workspace.getNode(workspace.getNextActionsNodeId()).orElseThrow()
                .getChildIds().contains(subId));
    }

    @Test
    void convertProjectToAction_subProject_setsStatusNext() throws IOException {
        var projectId = service.addInboxItem("Project");
        service.convertInboxItemToProject(projectId);
        var subId = service.addChild(projectId, "Sub");
        service.convertProjectToAction(subId);
        assertEquals(NodeStatus.NEXT, workspace.getNode(subId).orElseThrow().getStatus());
    }

    @Test
    void convertProjectToAction_savesWorkspace() throws IOException {
        var id = service.addInboxItem("Task");
        service.convertInboxItemToProject(id);
        repository.saveCount = 0;
        service.convertProjectToAction(id);
        assertEquals(1, repository.saveCount);
    }

    @Test
    void convertProjectToAction_throwsIfHasChildren() throws IOException {
        var id = service.addInboxItem("Task");
        service.convertInboxItemToProject(id);
        service.addChild(id, "Child action");
        assertThrows(IllegalStateException.class,
                () -> service.convertProjectToAction(id));
    }

    @Test
    void convertProjectToAction_throwsForUnknownId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.convertProjectToAction(UUID.randomUUID()));
    }

    // --- createBacklogAction ---

    @Test
    void createBacklogAction_addsNodeWithBacklogStatus() throws IOException {
        var id = service.createBacklogAction("Learn piano");
        assertEquals(NodeStatus.BACKLOG, workspace.getNode(id).orElseThrow().getStatus());
    }

    @Test
    void createBacklogAction_addsToActionsArea() throws IOException {
        var id = service.createBacklogAction("Learn piano");
        assertTrue(workspace.getNode(workspace.getNextActionsNodeId()).orElseThrow()
                .getChildIds().contains(id));
    }

    @Test
    void createBacklogAction_savesWorkspace() throws IOException {
        service.createBacklogAction("Learn piano");
        assertEquals(1, repository.saveCount);
    }

    // --- createNextAction (tagged) ---

    @Test
    void createNextAction_withTags_setsTagsOnNode() throws IOException {
        var id = service.createNextAction("Write report", List.of("@computer", "@office"));
        assertEquals(List.of("@computer", "@office"),
                workspace.getNode(id).orElseThrow().getTags());
    }

    @Test
    void createNextAction_withTags_savesWorkspace() throws IOException {
        service.createNextAction("Write report", List.of("@computer"));
        assertEquals(1, repository.saveCount);
    }

    // --- getViewOrder / moveViewItemUp / moveViewItemDown ---

    @Test
    void getViewOrder_emptyOrder_returnsLiveOrder() throws IOException {
        var a = service.addChild(rootId, "A");
        var b = service.addChild(rootId, "B");
        var live = List.of(workspace.getNode(a).orElseThrow(), workspace.getNode(b).orElseThrow());
        var result = service.getViewOrder("test-view", live);
        assertEquals(live, result);
    }

    @Test
    void moveViewItemUp_swapsInSavedOrder() throws IOException {
        var a = service.addChild(rootId, "A");
        var b = service.addChild(rootId, "B");
        var nodeA = workspace.getNode(a).orElseThrow();
        var nodeB = workspace.getNode(b).orElseThrow();
        var live = List.of(nodeA, nodeB);
        var currentOrder = List.of(a, b);
        service.moveViewItemUp("test-view", b, currentOrder);
        var result = service.getViewOrder("test-view", live);
        assertEquals(List.of(nodeB, nodeA), result);
    }

    @Test
    void moveViewItemDown_swapsInSavedOrder() throws IOException {
        var a = service.addChild(rootId, "A");
        var b = service.addChild(rootId, "B");
        var nodeA = workspace.getNode(a).orElseThrow();
        var nodeB = workspace.getNode(b).orElseThrow();
        var live = List.of(nodeA, nodeB);
        var currentOrder = List.of(a, b);
        service.moveViewItemDown("test-view", a, currentOrder);
        var result = service.getViewOrder("test-view", live);
        assertEquals(List.of(nodeB, nodeA), result);
    }

    @Test
    void moveViewItemUp_isNoOpForFirstItem() throws IOException {
        var a = service.addChild(rootId, "A");
        var b = service.addChild(rootId, "B");
        var nodeA = workspace.getNode(a).orElseThrow();
        var nodeB = workspace.getNode(b).orElseThrow();
        var currentOrder = List.of(a, b);
        service.moveViewItemUp("test-view", a, currentOrder);
        assertEquals(List.of(nodeA, nodeB), service.getViewOrder("test-view", List.of(nodeA, nodeB)));
    }

    @Test
    void moveViewItemDown_isNoOpForLastItem() throws IOException {
        var a = service.addChild(rootId, "A");
        var b = service.addChild(rootId, "B");
        var nodeA = workspace.getNode(a).orElseThrow();
        var nodeB = workspace.getNode(b).orElseThrow();
        var currentOrder = List.of(a, b);
        service.moveViewItemDown("test-view", b, currentOrder);
        assertEquals(List.of(nodeA, nodeB), service.getViewOrder("test-view", List.of(nodeA, nodeB)));
    }

    @Test
    void moveViewItemUp_savesWorkspace() throws IOException {
        var a = service.addChild(rootId, "A");
        var b = service.addChild(rootId, "B");
        repository.saveCount = 0;
        service.moveViewItemUp("test-view", b, List.of(a, b));
        assertEquals(1, repository.saveCount);
    }

    // --- createNextAction ---

    @Test
    void createNextAction_addsNodeWithNextStatus() throws IOException {
        var id = service.createNextAction("Call bank");
        var node = workspace.getNode(id).orElseThrow();
        assertEquals("Call bank", node.getTitle());
        assertEquals(NodeStatus.NEXT, node.getStatus());
    }

    @Test
    void createNextAction_addsToActionsArea() throws IOException {
        var id = service.createNextAction("Call bank");
        assertTrue(workspace.getNode(workspace.getNextActionsNodeId()).orElseThrow()
                .getChildIds().contains(id));
    }

    @Test
    void createNextAction_savesWorkspace() throws IOException {
        service.createNextAction("Call bank");
        assertEquals(1, repository.saveCount);
    }

    // --- addSubProject ---

    @Test
    void addSubProject_setsProjectFlag() throws IOException {
        var projectId = service.addInboxItem("Parent");
        service.convertInboxItemToProject(projectId);
        var subId = service.addSubProject(projectId, "Sub");
        assertTrue(workspace.getNode(subId).orElseThrow().isProject());
    }

    @Test
    void addSubProject_addsChildToParent() throws IOException {
        var projectId = service.addInboxItem("Parent");
        service.convertInboxItemToProject(projectId);
        var subId = service.addSubProject(projectId, "Sub");
        assertTrue(workspace.getNode(projectId).orElseThrow().getChildIds().contains(subId));
    }

    @Test
    void addChild_setsProjectFlagWhenParentIsProjectsNode() throws IOException {
        var id = service.addChild(workspace.getProjectsNodeId(), "Direct project");
        assertTrue(workspace.getNode(id).orElseThrow().isProject());
    }

    @Test
    void addChild_doesNotSetProjectFlagForOtherParents() throws IOException {
        var id = service.addChild(rootId, "Regular child");
        assertFalse(workspace.getNode(id).orElseThrow().isProject());
    }

    @Test
    void convertInboxItemToProject_setsProjectFlag() throws IOException {
        var id = service.addInboxItem("Task");
        service.convertInboxItemToProject(id);
        assertTrue(workspace.getNode(id).orElseThrow().isProject());
    }

    // --- saveAsTemplate / deleteTemplate / applyTemplate ---

    @Test
    void saveAsTemplate_addsTemplateWithName() throws IOException {
        var projectId = service.addInboxItem("Travel");
        service.convertInboxItemToProject(projectId);
        service.addChild(projectId, "Book flights");
        service.addChild(projectId, "Book hotel");
        service.saveAsTemplate("Travel template", projectId);
        assertEquals(1, workspace.getTemplates().size());
        assertEquals("Travel template", workspace.getTemplates().get(0).name());
    }

    @Test
    void saveAsTemplate_capturesFullSubtree() throws IOException {
        var projectId = service.addInboxItem("Travel");
        service.convertInboxItemToProject(projectId);
        var flightsId = service.addChild(projectId, "Book flights");
        service.addChild(flightsId, "Outbound");
        service.addChild(flightsId, "Return");
        service.saveAsTemplate("Travel template", projectId);
        var children = workspace.getTemplates().get(0).children();
        assertEquals(1, children.size());
        assertEquals("Book flights", children.get(0).title());
        assertEquals(2, children.get(0).children().size());
    }

    @Test
    void saveAsTemplate_throwsOnBlankName() throws IOException {
        var projectId = service.addInboxItem("Travel");
        service.convertInboxItemToProject(projectId);
        assertThrows(IllegalArgumentException.class,
                () -> service.saveAsTemplate("  ", projectId));
    }

    @Test
    void saveAsTemplate_throwsOnDuplicateName() throws IOException {
        var projectId = service.addInboxItem("Travel");
        service.convertInboxItemToProject(projectId);
        service.saveAsTemplate("Travel template", projectId);
        assertThrows(IllegalArgumentException.class,
                () -> service.saveAsTemplate("Travel template", projectId));
    }

    @Test
    void saveAsTemplate_savesWorkspace() throws IOException {
        var projectId = service.addInboxItem("Travel");
        service.convertInboxItemToProject(projectId);
        repository.saveCount = 0;
        service.saveAsTemplate("Travel template", projectId);
        assertEquals(1, repository.saveCount);
    }

    @Test
    void deleteTemplate_removesTemplate() throws IOException {
        var projectId = service.addInboxItem("Travel");
        service.convertInboxItemToProject(projectId);
        service.saveAsTemplate("Travel template", projectId);
        repository.saveCount = 0;
        service.deleteTemplate("Travel template");
        assertTrue(workspace.getTemplates().isEmpty());
        assertEquals(1, repository.saveCount);
    }

    @Test
    void deleteTemplate_isNoOpWhenNotFound() throws IOException {
        service.deleteTemplate("Nonexistent");
        assertEquals(0, repository.saveCount);
    }

    @Test
    void applyTemplate_clonesChildrenUnderProject() throws IOException {
        var sourceId = service.addInboxItem("Travel");
        service.convertInboxItemToProject(sourceId);
        service.addChild(sourceId, "Book flights");
        service.addChild(sourceId, "Book hotel");
        service.saveAsTemplate("Travel template", sourceId);

        var targetId = service.addInboxItem("Japan trip");
        service.convertInboxItemToProject(targetId);
        service.applyTemplate(targetId, workspace.getTemplates().get(0));

        var children = workspace.getChildren(targetId);
        assertEquals(2, children.size());
        assertTrue(children.stream().anyMatch(n -> n.getTitle().equals("Book flights")));
        assertTrue(children.stream().anyMatch(n -> n.getTitle().equals("Book hotel")));
    }

    @Test
    void applyTemplate_assignsNewNodeIds() throws IOException {
        var sourceId = service.addInboxItem("Travel");
        service.convertInboxItemToProject(sourceId);
        var originalChild = service.addChild(sourceId, "Book flights");
        service.saveAsTemplate("Travel template", sourceId);

        var targetId = service.addInboxItem("Japan trip");
        service.convertInboxItemToProject(targetId);
        service.applyTemplate(targetId, workspace.getTemplates().get(0));

        var clonedId = workspace.getChildren(targetId).get(0).getId();
        assertNotEquals(originalChild, clonedId);
    }

    @Test
    void applyTemplate_clonesDeepSubtree() throws IOException {
        var sourceId = service.addInboxItem("Travel");
        service.convertInboxItemToProject(sourceId);
        var flightsId = service.addChild(sourceId, "Book flights");
        service.addChild(flightsId, "Outbound");
        service.addChild(flightsId, "Return");
        service.saveAsTemplate("Travel template", sourceId);

        var targetId = service.addInboxItem("Japan trip");
        service.convertInboxItemToProject(targetId);
        service.applyTemplate(targetId, workspace.getTemplates().get(0));

        var flightsClone = workspace.getChildren(targetId).get(0);
        assertEquals(2, workspace.getChildren(flightsClone.getId()).size());
    }

    @Test
    void applyTemplate_savesWorkspace() throws IOException {
        var sourceId = service.addInboxItem("Travel");
        service.convertInboxItemToProject(sourceId);
        service.addChild(sourceId, "Book flights");
        service.saveAsTemplate("Travel template", sourceId);

        var targetId = service.addInboxItem("Japan trip");
        service.convertInboxItemToProject(targetId);
        repository.saveCount = 0;
        service.applyTemplate(targetId, workspace.getTemplates().get(0));
        assertEquals(1, repository.saveCount);
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
