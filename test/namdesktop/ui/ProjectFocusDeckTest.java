package namdesktop.ui;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Covers the selector feeding the Project Workbench focus deck. */
class ProjectFocusDeckTest {

    private NamNode node(String title, NodeStatus status, boolean project) {
        var n = new NamNode(UUID.randomUUID(), title);
        n.setStatus(status);
        n.setProject(project);
        return n;
    }

    private NamWorkspace workspaceWith(NamNode project, NamNode... children) {
        var ws = new NamWorkspace();
        ws.getNodes().put(project.getId(), project);
        for (var c : children) {
            ws.getNodes().put(c.getId(), c);
            project.getChildIds().add(c.getId());
        }
        return ws;
    }

    @Test
    void selectsOpenDirectActionsOnly() {
        var project = node("Project", NodeStatus.NEXT, true);
        var open1   = node("open-next",    NodeStatus.NEXT,    false);
        var done    = node("done",         NodeStatus.DONE,    false);
        var open2   = node("open-backlog", NodeStatus.BACKLOG, false);
        var sub     = node("sub-project",  NodeStatus.NEXT,    true);
        var ws = workspaceWith(project, open1, done, open2, sub);

        var result = ProjectWorkbenchPanel.focusableDirectActions(ws, project.getId());

        // Done is excluded; the sub-project is excluded; order preserved.
        assertEquals(List.of(open1, open2), result);
    }

    @Test
    void emptyWhenNoOpenDirectActions() {
        var project = node("Project", NodeStatus.NEXT, true);
        var done    = node("done", NodeStatus.DONE, false);
        var sub     = node("sub",  NodeStatus.NEXT, true);
        var ws = workspaceWith(project, done, sub);

        assertTrue(ProjectWorkbenchPanel.focusableDirectActions(ws, project.getId()).isEmpty());
    }
}
