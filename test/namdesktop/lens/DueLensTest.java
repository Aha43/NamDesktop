package namdesktop.lens;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DueLensTest {

    private NamWorkspace workspace;
    private DueLens lens;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        workspace = NamWorkspace.createDefault();
        lens  = new DueLens();
        today = LocalDate.of(2026, 6, 10);
    }

    private NamNode addAction(String title, NodeStatus status, LocalDate dueAt) {
        var node = new NamNode(UUID.randomUUID(), title);
        node.setStatus(status);
        node.setDueAt(dueAt);
        workspace.getNodes().put(node.getId(), node);
        return node;
    }

    @Test
    void emptyWhenNoActionsHaveDueDate() {
        addAction("No due date", NodeStatus.NEXT, null);
        var result = lens.items(workspace, today);
        assertTrue(result.overdue().isEmpty());
        assertTrue(result.today().isEmpty());
        assertTrue(result.thisWeek().isEmpty());
        assertTrue(result.later().isEmpty());
    }

    @Test
    void overdueAction_appearsInOverdue() {
        addAction("Past", NodeStatus.NEXT, today.minusDays(2));
        var result = lens.items(workspace, today);
        assertEquals(1, result.overdue().size());
        assertEquals("Past", result.overdue().get(0).title());
    }

    @Test
    void todayAction_appearsInToday() {
        addAction("Due now", NodeStatus.NEXT, today);
        var result = lens.items(workspace, today);
        assertEquals(1, result.today().size());
        assertEquals("Due now", result.today().get(0).title());
    }

    @Test
    void thisWeekAction_appearsInThisWeek() {
        addAction("This week", NodeStatus.BACKLOG, today.plusDays(3));
        var result = lens.items(workspace, today);
        assertEquals(1, result.thisWeek().size());
        assertEquals("This week", result.thisWeek().get(0).title());
    }

    @Test
    void laterAction_appearsInLater() {
        addAction("Later", NodeStatus.NEXT, today.plusDays(14));
        var result = lens.items(workspace, today);
        assertEquals(1, result.later().size());
        assertEquals("Later", result.later().get(0).title());
    }

    @Test
    void doneActions_excluded() {
        addAction("Done past", NodeStatus.DONE, today.minusDays(1));
        var result = lens.items(workspace, today);
        assertTrue(result.overdue().isEmpty());
    }

    @Test
    void projects_excluded() {
        var proj = new NamNode(UUID.randomUUID(), "A project");
        proj.setProject(true);
        proj.setDueAt(today);
        workspace.getNodes().put(proj.getId(), proj);
        var result = lens.items(workspace, today);
        assertTrue(result.today().isEmpty());
    }

    @Test
    void bucketsAreSortedByDueAscending() {
        addAction("Later B", NodeStatus.NEXT, today.plusDays(10));
        addAction("Later A", NodeStatus.NEXT, today.plusDays(5));
        var result = lens.items(workspace, today);
        assertEquals("Later A", result.thisWeek().get(0).title());
        assertEquals("Later B", result.later().get(0).title());
    }

    @Test
    void boundary_sevenDaysIsThisWeek() {
        addAction("Exactly 7 days", NodeStatus.NEXT, today.plusDays(7));
        var result = lens.items(workspace, today);
        assertEquals(1, result.thisWeek().size());
        assertTrue(result.later().isEmpty());
    }

    @Test
    void boundary_eightDaysIsLater() {
        addAction("Eight days", NodeStatus.NEXT, today.plusDays(8));
        var result = lens.items(workspace, today);
        assertTrue(result.thisWeek().isEmpty());
        assertEquals(1, result.later().size());
    }
}
