package namdesktop.service;

import namdesktop.lens.ProjectWorkbenchLens;
import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.persist.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Column View MVP relies entirely on {@link NamWorkspaceService#moveNode} for its only
 * mutation: moving a card between columns reparents the action between sibling projects.
 * These tests lock that behaviour (the UI is a thin lens over it).
 */
class MoveBetweenColumnsTest {

    private NamWorkspace workspace;
    private NamWorkspaceService service;

    // parent project with two child-project "columns"
    private UUID parentId;
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

    // --- the core move ---

    @Test
    void move_reparentsActionToSiblingColumn() throws Exception {
        var action = addAction(childA, "Buy ticket");

        service.moveNode(action, childB);

        assertTrue(workspace.getNode(childB).orElseThrow().getChildIds().contains(action),
                "target column should now own the action");
        assertFalse(workspace.getNode(childA).orElseThrow().getChildIds().contains(action),
                "source column should no longer own the action");
    }

    @Test
    void move_stampsUpdatedAt() throws Exception {
        var action = addAction(childA, "Buy ticket");
        assertNull(workspace.getNode(action).orElseThrow().getUpdatedAt());

        service.moveNode(action, childB);

        assertNotNull(workspace.getNode(action).orElseThrow().getUpdatedAt());
    }

    @Test
    void move_unsortedParentActionIntoColumn() throws Exception {
        // an action directly under the parent project = the "Unsorted" column
        var action = addAction(parentId, "Decide dates");

        service.moveNode(action, childA);

        assertTrue(workspace.getNode(childA).orElseThrow().getChildIds().contains(action));
        assertFalse(workspace.getNode(parentId).orElseThrow().getChildIds().contains(action));
    }

    @Test
    void move_rejectsNonProjectTarget() {
        var action = addAction(childA, "Buy ticket");
        var sibling = addAction(childB, "Pack bag"); // an action, not a project

        assertThrows(IllegalArgumentException.class, () -> service.moveNode(action, sibling),
                "actions may only be moved into project nodes (columns are always projects)");
    }

    // --- columns = child projects contract (what buildColumnView renders) ---

    @Test
    void lens_childSectionsMapOneToOneToColumns() {
        addAction(childA, "Buy ticket");
        addAction(parentId, "Decide dates"); // unsorted

        var projection = new ProjectWorkbenchLens().project(workspace, parentId);

        assertEquals(2, projection.childSections().size(), "one column per child project");
        var columnTitles = projection.childSections().stream()
                .map(s -> s.project().getTitle()).toList();
        assertEquals(java.util.List.of("Travel", "Packing"), columnTitles);
        assertEquals(1, projection.directActions().size(), "parent's own actions form the Unsorted column");
        assertEquals("Decide dates", projection.directActions().get(0).getTitle());
    }

    // helpers

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
