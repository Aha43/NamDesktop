package namdesktop.ui;

import namdesktop.lens.MissionControlStation;
import namdesktop.model.NamNode;
import namdesktop.model.NodeStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnsortedStationTest {

    private static NamNode action(NodeStatus status) {
        var n = new NamNode(UUID.randomUUID(), "a");
        n.setStatus(status);
        return n;
    }

    @Test
    void countsDoneOverTotalDirectActions() {
        var projectId = UUID.randomUUID();
        var actions = List.of(
                action(NodeStatus.DONE),
                action(NodeStatus.DONE),
                action(NodeStatus.NEXT),
                action(NodeStatus.BACKLOG));
        var s = ProjectWorkbenchPanel.unsortedStation(projectId, actions);
        assertEquals(projectId, s.id());
        assertEquals("Unsorted", s.title());
        assertEquals(2, s.doneCount());
        assertEquals(4, s.totalActions());
        assertEquals(0, s.subProjectCount());
        assertEquals(MissionControlStation.HeatLevel.AMBER, s.heatLevel()); // 0.5
    }

    @Test
    void emptyDirectActions_isNeutral() {
        var s = ProjectWorkbenchPanel.unsortedStation(UUID.randomUUID(), List.of());
        assertEquals(0, s.totalActions());
        assertEquals(MissionControlStation.HeatLevel.NEUTRAL, s.heatLevel());
    }
}
