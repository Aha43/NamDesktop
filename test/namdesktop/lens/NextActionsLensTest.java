package namdesktop.lens;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void items_projectsTitleAndStatus() {
        var node = new NamNode(UUID.randomUUID(), "Call dentist");
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(workspace.getNextActionsNodeId()).orElseThrow()
                .getChildIds().add(node.getId());

        var rows = lens.items(workspace);
        assertEquals(1, rows.size());
        assertEquals("Call dentist",   rows.get(0).title());
        assertEquals(NodeStatus.ACTIVE, rows.get(0).status());
        assertEquals(node.getId(),      rows.get(0).id());
    }

    @Test
    void items_reflectsDoneStatus() {
        var node = new NamNode(UUID.randomUUID(), "Done action");
        node.setStatus(NodeStatus.DONE);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(workspace.getNextActionsNodeId()).orElseThrow()
                .getChildIds().add(node.getId());

        var rows = lens.items(workspace);
        assertEquals(NodeStatus.DONE, rows.get(0).status());
    }

    @Test
    void items_preservesOrder() {
        var first  = new NamNode(UUID.randomUUID(), "First");
        var second = new NamNode(UUID.randomUUID(), "Second");
        workspace.getNodes().put(first.getId(),  first);
        workspace.getNodes().put(second.getId(), second);
        var nextActionsChildIds = workspace.getNode(workspace.getNextActionsNodeId())
                .orElseThrow().getChildIds();
        nextActionsChildIds.add(first.getId());
        nextActionsChildIds.add(second.getId());

        var rows = lens.items(workspace);
        assertEquals(2,        rows.size());
        assertEquals("First",  rows.get(0).title());
        assertEquals("Second", rows.get(1).title());
    }
}
