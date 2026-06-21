package namdesktop.ui;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectPathSupportTest {

    // width = char count; separator " > " = 3 → titles ["AB","CDE"] lay out as:
    // seg0 [0..2], sep [2..5], seg1 [5..8]
    private static final ToIntFunction<String> WIDTH = String::length;
    private static final int SEP = ProjectPathSupport.SEPARATOR.length();

    @Test
    void segmentIndexAt_beforeStart_isMinusOne() {
        assertEquals(-1, ProjectPathSupport.segmentIndexAt(-1, List.of("AB", "CDE"), WIDTH, SEP));
    }

    @Test
    void segmentIndexAt_emptyTitles_isMinusOne() {
        assertEquals(-1, ProjectPathSupport.segmentIndexAt(0, List.of(), WIDTH, SEP));
    }

    @Test
    void segmentIndexAt_withinFirstSegment() {
        assertEquals(0, ProjectPathSupport.segmentIndexAt(0, List.of("AB", "CDE"), WIDTH, SEP));
        assertEquals(0, ProjectPathSupport.segmentIndexAt(2, List.of("AB", "CDE"), WIDTH, SEP));
    }

    @Test
    void segmentIndexAt_separatorAttributesToLeftSegment() {
        assertEquals(0, ProjectPathSupport.segmentIndexAt(4, List.of("AB", "CDE"), WIDTH, SEP));
    }

    @Test
    void segmentIndexAt_withinSecondSegment() {
        assertEquals(1, ProjectPathSupport.segmentIndexAt(6, List.of("AB", "CDE"), WIDTH, SEP));
        assertEquals(1, ProjectPathSupport.segmentIndexAt(8, List.of("AB", "CDE"), WIDTH, SEP));
    }

    @Test
    void segmentIndexAt_pastEnd_clampsToLastSegment() {
        assertEquals(1, ProjectPathSupport.segmentIndexAt(500, List.of("AB", "CDE"), WIDTH, SEP));
    }

    // --- forAction ---

    private NamWorkspace ws;

    private UUID addProject(UUID parent, String title) {
        var n = new NamNode(UUID.randomUUID(), title);
        n.setProject(true);
        ws.getNodes().put(n.getId(), n);
        ws.getNode(parent).orElseThrow().getChildIds().add(n.getId());
        return n.getId();
    }

    private UUID addAction(UUID parent, String title) {
        var n = new NamNode(UUID.randomUUID(), title);
        n.setStatus(NodeStatus.NEXT);
        ws.getNodes().put(n.getId(), n);
        ws.getNode(parent).orElseThrow().getChildIds().add(n.getId());
        return n.getId();
    }

    @Test
    void forAction_returnsAncestorProjectsRootToLeaf_skippingStructural() {
        ws = NamWorkspace.createDefault();
        var top    = addProject(ws.getProjectsNodeId(), "Trip to Rome");
        var sub    = addProject(top, "Flights");
        var action = addAction(sub, "Book seat");

        var segs = ProjectPathSupport.forAction(ws, action);
        assertEquals(List.of("Trip to Rome", "Flights"), segs.stream().map(ProjectPathSupport.Segment::title).toList());
        assertEquals(top, segs.get(0).id());
        assertEquals(sub, segs.get(1).id());
    }

    @Test
    void forAction_freeActionUnderActionsArea_isEmpty() {
        ws = NamWorkspace.createDefault();
        var action = addAction(ws.getNextActionsNodeId(), "Loose action");
        assertTrue(ProjectPathSupport.forAction(ws, action).isEmpty());
    }

    @Test
    void forAction_nullId_isEmpty() {
        ws = NamWorkspace.createDefault();
        assertTrue(ProjectPathSupport.forAction(ws, null).isEmpty());
    }
}
