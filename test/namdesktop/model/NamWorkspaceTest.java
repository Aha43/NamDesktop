package namdesktop.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NamWorkspaceTest {

    @Test
    void createDefault_rootNodeExists() {
        var ws = NamWorkspace.createDefault();
        assertNotNull(ws.getRootNodeId());
        assertTrue(ws.getNode(ws.getRootNodeId()).isPresent());
    }

    @Test
    void createDefault_rootHasExpectedTitleAndStatus() {
        var ws = NamWorkspace.createDefault();
        var root = ws.getNode(ws.getRootNodeId()).orElseThrow();
        assertEquals("NAM", root.getTitle());
        assertEquals(NodeStatus.BACKLOG, root.getStatus());
    }

    @Test
    void createDefault_rootHasThreeChildren() {
        var ws = NamWorkspace.createDefault();
        var rootChildren = ws.getChildren(ws.getRootNodeId());
        assertEquals(3, rootChildren.size());
        var childIds = rootChildren.stream().map(NamNode::getId).toList();
        assertTrue(childIds.contains(ws.getInboxNodeId()));
        assertTrue(childIds.contains(ws.getProjectsNodeId()));
        assertTrue(childIds.contains(ws.getNextActionsNodeId()));
    }

    @Test
    void getNode_returnsNodeForKnownId() {
        var ws = NamWorkspace.createDefault();
        var rootId = ws.getRootNodeId();
        var result = ws.getNode(rootId);
        assertTrue(result.isPresent());
        assertEquals(rootId, result.get().getId());
    }

    @Test
    void getNode_returnsEmptyForUnknownId() {
        var ws = NamWorkspace.createDefault();
        assertTrue(ws.getNode(UUID.randomUUID()).isEmpty());
    }

    @Test
    void getChildren_returnsChildrenInInsertionOrder() {
        var ws = NamWorkspace.createDefault();
        var parent = new NamNode(UUID.randomUUID(), "Parent");
        var first  = new NamNode(UUID.randomUUID(), "First");
        var second = new NamNode(UUID.randomUUID(), "Second");
        ws.getNodes().put(parent.getId(), parent);
        ws.getNodes().put(first.getId(),  first);
        ws.getNodes().put(second.getId(), second);
        parent.getChildIds().add(first.getId());
        parent.getChildIds().add(second.getId());

        var children = ws.getChildren(parent.getId());
        assertEquals(2, children.size());
        assertEquals("First",  children.get(0).getTitle());
        assertEquals("Second", children.get(1).getTitle());
    }

    @Test
    void getChildren_returnsEmptyListForUnknownParent() {
        var ws = NamWorkspace.createDefault();
        assertTrue(ws.getChildren(UUID.randomUUID()).isEmpty());
    }

    @Test
    void getChildren_skipsDanglingChildIds() {
        var ws = NamWorkspace.createDefault();
        var parent = new NamNode(UUID.randomUUID(), "Parent");
        var real   = new NamNode(UUID.randomUUID(), "Real");
        ws.getNodes().put(parent.getId(), parent);
        ws.getNodes().put(real.getId(),   real);
        parent.getChildIds().add(UUID.randomUUID()); // dangling
        parent.getChildIds().add(real.getId());

        var children = ws.getChildren(parent.getId());
        assertEquals(1, children.size());
        assertEquals("Real", children.get(0).getTitle());
    }

    // --- well-known areas ---

    @Test
    void createDefault_hasInboxNode() {
        var ws = NamWorkspace.createDefault();
        assertNotNull(ws.getInboxNodeId());
        var inbox = ws.getNode(ws.getInboxNodeId()).orElseThrow();
        assertEquals("Inbox", inbox.getTitle());
        assertTrue(ws.getNode(ws.getRootNodeId()).orElseThrow()
                .getChildIds().contains(ws.getInboxNodeId()));
    }

    @Test
    void createDefault_hasProjectsNode() {
        var ws = NamWorkspace.createDefault();
        assertNotNull(ws.getProjectsNodeId());
        var projects = ws.getNode(ws.getProjectsNodeId()).orElseThrow();
        assertEquals("Projects", projects.getTitle());
        assertTrue(ws.getNode(ws.getRootNodeId()).orElseThrow()
                .getChildIds().contains(ws.getProjectsNodeId()));
    }

    @Test
    void createDefault_hasNextActionsNode() {
        var ws = NamWorkspace.createDefault();
        assertNotNull(ws.getNextActionsNodeId());
        var nextActions = ws.getNode(ws.getNextActionsNodeId()).orElseThrow();
        assertEquals("Next Actions", nextActions.getTitle());
        assertTrue(ws.getNode(ws.getRootNodeId()).orElseThrow()
                .getChildIds().contains(ws.getNextActionsNodeId()));
    }

    @Test
    void getInboxItems_returnsEmptyInitially() {
        var ws = NamWorkspace.createDefault();
        assertTrue(ws.getInboxItems().isEmpty());
    }

    @Test
    void getInboxItems_returnsItemsInInsertionOrder() {
        var ws = NamWorkspace.createDefault();
        var first  = new NamNode(UUID.randomUUID(), "First");
        var second = new NamNode(UUID.randomUUID(), "Second");
        ws.getNodes().put(first.getId(),  first);
        ws.getNodes().put(second.getId(), second);
        ws.getNode(ws.getInboxNodeId()).orElseThrow().getChildIds().add(first.getId());
        ws.getNode(ws.getInboxNodeId()).orElseThrow().getChildIds().add(second.getId());

        var items = ws.getInboxItems();
        assertEquals(2, items.size());
        assertEquals("First",  items.get(0).getTitle());
        assertEquals("Second", items.get(1).getTitle());
    }
}
