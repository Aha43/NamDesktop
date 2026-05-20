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
        assertEquals(NodeStatus.ACTIVE, root.getStatus());
    }

    @Test
    void createDefault_rootHasNoChildren() {
        var ws = NamWorkspace.createDefault();
        assertTrue(ws.getChildren(ws.getRootNodeId()).isEmpty());
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
        var rootId = ws.getRootNodeId();

        var first = new NamNode(UUID.randomUUID(), "First");
        var second = new NamNode(UUID.randomUUID(), "Second");
        ws.getNodes().put(first.getId(), first);
        ws.getNodes().put(second.getId(), second);
        ws.getNode(rootId).orElseThrow().getChildIds().add(first.getId());
        ws.getNode(rootId).orElseThrow().getChildIds().add(second.getId());

        var children = ws.getChildren(rootId);
        assertEquals(2, children.size());
        assertEquals("First", children.get(0).getTitle());
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
        var rootId = ws.getRootNodeId();

        var real = new NamNode(UUID.randomUUID(), "Real");
        ws.getNodes().put(real.getId(), real);
        ws.getNode(rootId).orElseThrow().getChildIds().add(UUID.randomUUID()); // dangling
        ws.getNode(rootId).orElseThrow().getChildIds().add(real.getId());

        var children = ws.getChildren(rootId);
        assertEquals(1, children.size());
        assertEquals("Real", children.get(0).getTitle());
    }
}
