package namdesktop.lens;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NextActionsLensTest {

    private NamWorkspace workspace;
    private NextActionsLens lens;

    @BeforeEach
    void setUp() {
        workspace = NamWorkspace.createDefault();
        lens = new NextActionsLens();
    }

    @Test
    void items_returnsEmptyListWhenNoNextActions() {
        assertTrue(lens.items(workspace).isEmpty());
    }

    @Test
    void items_showsNodeWithNextStatus() {
        var node = new NamNode(UUID.randomUUID(), "Call dentist");
        node.setStatus(NodeStatus.NEXT);
        workspace.getNodes().put(node.getId(), node);

        var rows = lens.items(workspace);
        assertEquals(1, rows.size());
        assertEquals("Call dentist", rows.get(0).title());
        assertEquals(NodeStatus.NEXT, rows.get(0).status());
        assertEquals(node.getId(),    rows.get(0).id());
        assertNull(rows.get(0).parentTitle());
    }

    @Test
    void items_propagatesTimestamps() {
        var node = new NamNode(UUID.randomUUID(), "Stamped action");
        node.setStatus(NodeStatus.NEXT);
        var updated = LocalDateTime.of(2026, 3, 5, 12, 0);
        var created = LocalDateTime.of(2026, 3, 1, 8, 0);
        node.setUpdatedAt(updated);
        node.setCreatedAt(created);
        workspace.getNodes().put(node.getId(), node);

        var row = lens.items(workspace).get(0);
        assertEquals(updated, row.updatedAt());
        assertEquals(created, row.createdAt());
    }

    @Test
    void items_timestampsAreNullWhenNotSet() {
        var node = new NamNode(UUID.randomUUID(), "No stamp");
        node.setStatus(NodeStatus.NEXT);
        workspace.getNodes().put(node.getId(), node);

        var row = lens.items(workspace).get(0);
        assertNull(row.updatedAt());
        assertNull(row.createdAt());
    }

    @Test
    void items_doesNotShowBacklogNodes() {
        var node = new NamNode(UUID.randomUUID(), "Backlog task");
        node.setStatus(NodeStatus.BACKLOG);
        workspace.getNodes().put(node.getId(), node);

        assertTrue(lens.items(workspace).isEmpty());
    }

    @Test
    void items_doesNotShowDoneNodes() {
        var node = new NamNode(UUID.randomUUID(), "Done action");
        node.setStatus(NodeStatus.DONE);
        workspace.getNodes().put(node.getId(), node);

        assertTrue(lens.items(workspace).isEmpty());
    }

    @Test
    void items_showsNextNodeRegardlessOfStructuralParent() {
        // A node under a project with NEXT status must appear in the lens
        var projectNode = new NamNode(UUID.randomUUID(), "My Project");
        var actionNode  = new NamNode(UUID.randomUUID(), "Write report");
        actionNode.setStatus(NodeStatus.NEXT);
        workspace.getNodes().put(projectNode.getId(), projectNode);
        workspace.getNodes().put(actionNode.getId(),  actionNode);
        projectNode.getChildIds().add(actionNode.getId());

        var rows = lens.items(workspace);
        assertEquals(1, rows.size());
        assertEquals("Write report", rows.get(0).title());
        assertEquals("My Project",   rows.get(0).parentTitle());
    }

    @Test
    void items_parentTitleIsNullForStandaloneAction() {
        var actionNode = new NamNode(UUID.randomUUID(), "Buy milk");
        actionNode.setStatus(NodeStatus.NEXT);
        workspace.getNodes().put(actionNode.getId(), actionNode);

        var rows = lens.items(workspace);
        assertEquals(1, rows.size());
        assertNull(rows.get(0).parentTitle());
    }

    @Test
    void items_doesNotShowStructuralNodes() {
        // Even if a structural node somehow gets NEXT status, it must be excluded
        workspace.getNode(workspace.getRootNodeId()).orElseThrow()
                .setStatus(NodeStatus.NEXT);

        assertTrue(lens.items(workspace).isEmpty());
    }

    @Test
    void items_doesNotShowProjects() {
        var project = new NamNode(UUID.randomUUID(), "Big project");
        project.setProject(true);
        project.setStatus(NodeStatus.NEXT);
        workspace.getNodes().put(project.getId(), project);

        assertTrue(lens.items(workspace).isEmpty());
    }

    @Test
    void items_preservesInsertionOrder() {
        var first  = new NamNode(UUID.randomUUID(), "First");
        var second = new NamNode(UUID.randomUUID(), "Second");
        first.setStatus(NodeStatus.NEXT);
        second.setStatus(NodeStatus.NEXT);
        workspace.getNodes().put(first.getId(),  first);
        workspace.getNodes().put(second.getId(), second);

        var rows = lens.items(workspace);
        assertEquals(2,        rows.size());
        assertEquals("First",  rows.get(0).title());
        assertEquals("Second", rows.get(1).title());
    }
}
