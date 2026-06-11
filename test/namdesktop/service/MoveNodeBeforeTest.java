package namdesktop.service;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.persist.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Column-view drag-and-drop is a thin shell over {@link NamWorkspaceService#moveNodeBefore},
 * which handles both within-column reorder (same parent) and positioned cross-column move
 * (reparent + insert before an anchor). These tests lock that primitive.
 */
class MoveNodeBeforeTest {

    private NamWorkspace workspace;
    private NamWorkspaceService service;
    private UUID parentId;   // the "Unsorted" column
    private UUID childA;
    private UUID childB;

    @BeforeEach
    void setUp() {
        workspace = NamWorkspace.createDefault();
        service = new NamWorkspaceService(workspace, new InMemoryRepository(), Path.of("unused"));
        parentId = addProject(workspace.getProjectsNodeId(), "Trip");
        childA   = addProject(parentId, "Travel");
        childB   = addProject(parentId, "Packing");
    }

    // --- within-column reorder ---

    @Test
    void reorder_movesActionBeforeEarlierSibling() throws Exception {
        var a = addAction(childA, "A");
        var b = addAction(childA, "B");
        var c = addAction(childA, "C");

        service.moveNodeBefore(c, childA, a); // C before A

        assertEquals(List.of(c, a, b), actionIdsOf(childA));
    }

    @Test
    void reorder_nullAnchorAppendsToEnd() throws Exception {
        var a = addAction(childA, "A");
        var b = addAction(childA, "B");

        service.moveNodeBefore(a, childA, null); // A to the end

        assertEquals(List.of(b, a), actionIdsOf(childA));
    }

    // --- positioned cross-column move ---

    @Test
    void crossColumn_insertsBeforeAnchorInTarget() throws Exception {
        var x = addAction(childB, "X");
        var y = addAction(childB, "Y");
        var moved = addAction(childA, "Moved");

        service.moveNodeBefore(moved, childB, y); // into Packing, before Y

        assertEquals(List.of(x, moved, y), actionIdsOf(childB));
        assertTrue(actionIdsOf(childA).isEmpty(), "source column should no longer own it");
    }

    @Test
    void crossColumn_nullAnchorAppends_andMoveNodeDelegatesToThis() throws Exception {
        var x = addAction(childB, "X");
        var moved = addAction(childA, "Moved");

        service.moveNode(moved, childB); // delegates to moveNodeBefore(.., null)

        assertEquals(List.of(x, moved), actionIdsOf(childB));
    }

    @Test
    void crossColumn_reparentsSubProjectBetweenColumns() throws Exception {
        // split-lane mode lets a sub-project be dragged column-to-column
        var existing = addProject(childB, "B-sub");
        var moved    = addProject(childA, "A-sub");

        service.moveNodeBefore(moved, childB, existing); // A-sub before B-sub under Packing

        assertEquals(List.of(moved, existing), projectIdsOf(childB));
        assertTrue(projectIdsOf(childA).isEmpty(), "source column no longer owns the sub-project");
    }

    @Test
    void move_stampsUpdatedAt() throws Exception {
        var a = addAction(childA, "A");
        assertNull(workspace.getNode(a).orElseThrow().getUpdatedAt());

        service.moveNodeBefore(a, childB, null);

        assertNotNull(workspace.getNode(a).orElseThrow().getUpdatedAt());
    }

    // --- guards (shared with moveNode) ---

    @Test
    void rejectsNonProjectTarget() {
        var a = addAction(childA, "A");
        var sibling = addAction(childB, "Sibling"); // an action, not a project
        assertThrows(IllegalArgumentException.class, () -> service.moveNodeBefore(a, sibling, null));
    }

    @Test
    void rejectsMoveIntoOwnDescendant() {
        var nested = addProject(childA, "Nested");
        assertThrows(IllegalArgumentException.class, () -> service.moveNodeBefore(childA, nested, null));
    }

    // helpers

    private List<UUID> actionIdsOf(UUID projectId) {
        return workspace.getNode(projectId).orElseThrow().getChildIds().stream()
                .filter(id -> workspace.getNode(id).map(n -> !n.isProject()).orElse(false))
                .toList();
    }

    private List<UUID> projectIdsOf(UUID projectId) {
        return workspace.getNode(projectId).orElseThrow().getChildIds().stream()
                .filter(id -> workspace.getNode(id).map(NamNode::isProject).orElse(false))
                .toList();
    }

    private UUID addProject(UUID parent, String title) {
        var node = new NamNode(UUID.randomUUID(), title);
        node.setProject(true);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(parent).orElseThrow().getChildIds().add(node.getId());
        return node.getId();
    }

    private UUID addAction(UUID parent, String title) {
        var node = new NamNode(UUID.randomUUID(), title);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(parent).orElseThrow().getChildIds().add(node.getId());
        return node.getId();
    }

    private static final class InMemoryRepository implements WorkspaceRepository {
        @Override public NamWorkspace load(Path path) { return NamWorkspace.createDefault(); }
        @Override public void save(Path path, NamWorkspace workspace) { }
    }
}
