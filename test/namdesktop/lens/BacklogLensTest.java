package namdesktop.lens;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BacklogLensTest {

    private NamWorkspace workspace;
    private BacklogLens lens;

    @BeforeEach
    void setUp() {
        workspace = NamWorkspace.createDefault();
        lens = new BacklogLens();
    }

    @Test
    void items_returnsEmptyListWhenNoBacklogItems() {
        assertTrue(lens.items(workspace).isEmpty());
    }

    @Test
    void items_showsNodeWithBacklogStatus() {
        var node = new NamNode(UUID.randomUUID(), "Write tests");
        node.setStatus(NodeStatus.BACKLOG);
        workspace.getNodes().put(node.getId(), node);

        var rows = lens.items(workspace);
        assertEquals(1, rows.size());
        assertEquals("Write tests",      rows.get(0).title());
        assertEquals(NodeStatus.BACKLOG, rows.get(0).status());
        assertEquals(node.getId(),       rows.get(0).id());
        assertNull(rows.get(0).parentTitle());
    }

    @Test
    void items_doesNotShowNextNodes() {
        var node = new NamNode(UUID.randomUUID(), "Next task");
        node.setStatus(NodeStatus.NEXT);
        workspace.getNodes().put(node.getId(), node);

        assertTrue(lens.items(workspace).isEmpty());
    }

    @Test
    void items_doesNotShowDoneNodes() {
        var node = new NamNode(UUID.randomUUID(), "Done task");
        node.setStatus(NodeStatus.DONE);
        workspace.getNodes().put(node.getId(), node);

        assertTrue(lens.items(workspace).isEmpty());
    }

    @Test
    void items_showsBacklogNodeRegardlessOfStructuralParent() {
        var projectNode = new NamNode(UUID.randomUUID(), "My Project");
        projectNode.setStatus(NodeStatus.NEXT);
        var actionNode  = new NamNode(UUID.randomUUID(), "Future task");
        actionNode.setStatus(NodeStatus.BACKLOG);
        workspace.getNodes().put(projectNode.getId(), projectNode);
        workspace.getNodes().put(actionNode.getId(),  actionNode);
        projectNode.getChildIds().add(actionNode.getId());

        var rows = lens.items(workspace);
        assertEquals(1, rows.size());
        assertEquals("Future task", rows.get(0).title());
        assertEquals("My Project",  rows.get(0).parentTitle());
    }

    @Test
    void items_parentTitleIsNullForStandaloneAction() {
        var actionNode = new NamNode(UUID.randomUUID(), "Someday task");
        actionNode.setStatus(NodeStatus.BACKLOG);
        workspace.getNodes().put(actionNode.getId(), actionNode);

        var rows = lens.items(workspace);
        assertEquals(1, rows.size());
        assertNull(rows.get(0).parentTitle());
    }

    @Test
    void items_doesNotShowStructuralNodes() {
        workspace.getNode(workspace.getRootNodeId()).orElseThrow()
                .setStatus(NodeStatus.BACKLOG);

        assertTrue(lens.items(workspace).isEmpty());
    }

    @Test
    void items_doesNotShowProjects() {
        var project = new NamNode(UUID.randomUUID(), "Big project");
        project.setProject(true);
        project.setStatus(NodeStatus.BACKLOG);
        workspace.getNodes().put(project.getId(), project);

        assertTrue(lens.items(workspace).isEmpty());
    }

    @Test
    void items_inboxItem_isExcludedFromBacklog() {
        var node = new NamNode(UUID.randomUUID(), "Unprocessed capture");
        node.setStatus(NodeStatus.BACKLOG);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(workspace.getInboxNodeId()).orElseThrow().getChildIds().add(node.getId());

        var rows = lens.items(workspace);
        assertTrue(rows.isEmpty(), "Inbox items must not appear in Backlog (Option A)");
    }

    @Test
    void items_regularAction_appearsInBacklog() {
        var node = new NamNode(UUID.randomUUID(), "Regular action");
        node.setStatus(NodeStatus.BACKLOG);
        workspace.getNodes().put(node.getId(), node);

        var rows = lens.items(workspace);
        assertEquals(1, rows.size());
        assertEquals("Regular action", rows.get(0).title());
    }

    @Test
    void items_propagatesTimestamps() {
        var node = new NamNode(UUID.randomUUID(), "Stamped");
        node.setStatus(NodeStatus.BACKLOG);
        var updated = LocalDateTime.of(2026, 2, 15, 10, 0);
        var created = LocalDateTime.of(2026, 2, 1, 8, 0);
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
        node.setStatus(NodeStatus.BACKLOG);
        workspace.getNodes().put(node.getId(), node);

        var row = lens.items(workspace).get(0);
        assertNull(row.updatedAt());
        assertNull(row.createdAt());
    }

    @Test
    void items_preservesInsertionOrder() {
        var first  = new NamNode(UUID.randomUUID(), "First");
        var second = new NamNode(UUID.randomUUID(), "Second");
        first.setStatus(NodeStatus.BACKLOG);
        second.setStatus(NodeStatus.BACKLOG);
        workspace.getNodes().put(first.getId(),  first);
        workspace.getNodes().put(second.getId(), second);

        var rows = lens.items(workspace);
        assertEquals(2,        rows.size());
        assertEquals("First",  rows.get(0).title());
        assertEquals("Second", rows.get(1).title());
    }
}
