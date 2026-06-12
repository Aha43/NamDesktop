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
 * Column view's ◀ ▶ reorder rides {@link NamWorkspaceService#moveProjectUp} /
 * {@code moveProjectDown}, which reorders the parent's child *projects* (the columns).
 */
class ColumnReorderTest {

    private NamWorkspace workspace;
    private NamWorkspaceService service;
    private UUID parentId;
    private UUID a, b, c;

    @BeforeEach
    void setUp() {
        workspace = NamWorkspace.createDefault();
        service = new NamWorkspaceService(workspace, new InMemoryRepository(), Path.of("unused"));
        parentId = addProject(workspace.getProjectsNodeId(), "Trip");
        a = addProject(parentId, "A");
        b = addProject(parentId, "B");
        c = addProject(parentId, "C");
    }

    @Test
    void moveProjectUp_movesColumnEarlier() throws Exception {
        service.moveProjectUp(parentId, c); // C before B
        assertEquals(List.of("A", "C", "B"), columnOrder());
    }

    @Test
    void moveProjectDown_movesColumnLater() throws Exception {
        service.moveProjectDown(parentId, a); // A after B
        assertEquals(List.of("B", "A", "C"), columnOrder());
    }

    @Test
    void moveProjectUp_atFirstIsNoOp() throws Exception {
        service.moveProjectUp(parentId, a);
        assertEquals(List.of("A", "B", "C"), columnOrder());
    }

    @Test
    void reorder_leavesActionsUntouched() throws Exception {
        var act = addAction(parentId, "loose action");
        service.moveProjectDown(parentId, a);
        assertEquals(List.of("B", "A", "C"), columnOrder());
        // the action is still a direct child of the parent
        assertTrue(workspace.getNode(parentId).orElseThrow().getChildIds().contains(act));
    }

    private List<String> columnOrder() {
        return workspace.getChildren(parentId).stream()
                .filter(NamNode::isProject)
                .map(NamNode::getTitle)
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
