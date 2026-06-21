package namdesktop.lens;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProjectSummaryTest {

    private NamWorkspace ws;

    private UUID addProject(UUID parent, String title, String... tags) {
        var n = new NamNode(UUID.randomUUID(), title);
        n.setProject(true);
        for (var t : tags) n.getTags().add(t);
        ws.getNodes().put(n.getId(), n);
        ws.getNode(parent).orElseThrow().getChildIds().add(n.getId());
        return n.getId();
    }

    private UUID addAction(UUID parent, String title, NodeStatus status) {
        var n = new NamNode(UUID.randomUUID(), title);
        n.setStatus(status);
        ws.getNodes().put(n.getId(), n);
        ws.getNode(parent).orElseThrow().getChildIds().add(n.getId());
        return n.getId();
    }

    @Test
    void rendersHeadingTagsAndCheckboxes() {
        ws = NamWorkspace.createDefault();
        var p = addProject(ws.getProjectsNodeId(), "Trip to Rome", "@travel");
        addAction(p, "Book flights", NodeStatus.NEXT);
        addAction(p, "Pack bag", NodeStatus.BACKLOG);
        addAction(p, "Get passport", NodeStatus.DONE);

        var md = ProjectSummary.markdown(ws, p, true);
        assertTrue(md.startsWith("# Trip to Rome\n"), md);
        assertTrue(md.contains("_@travel_"), md);
        assertTrue(md.contains("- [ ] Book flights\n"), md);            // next → no suffix
        assertTrue(md.contains("- [ ] Pack bag _(backlog)_\n"), md);    // backlog → suffix
        assertTrue(md.contains("- [x] Get passport\n"), md);            // done → checked
    }

    @Test
    void includeSubProjects_nestsAsDeeperHeadings() {
        ws = NamWorkspace.createDefault();
        var p   = addProject(ws.getProjectsNodeId(), "Home");
        addAction(p, "Own action", NodeStatus.NEXT);
        var sub = addProject(p, "Kitchen");
        addAction(sub, "Get quotes", NodeStatus.NEXT);

        var md = ProjectSummary.markdown(ws, p, true);
        assertTrue(md.contains("## Kitchen"), md);
        assertTrue(md.contains("- [ ] Get quotes"), md);
    }

    @Test
    void currentProjectOnly_omitsSubProjects() {
        ws = NamWorkspace.createDefault();
        var p   = addProject(ws.getProjectsNodeId(), "Home");
        addAction(p, "Own action", NodeStatus.NEXT);
        var sub = addProject(p, "Kitchen");
        addAction(sub, "Get quotes", NodeStatus.NEXT);

        var md = ProjectSummary.markdown(ws, p, false);
        assertTrue(md.contains("- [ ] Own action"), md);
        assertFalse(md.contains("Kitchen"), md);
        assertFalse(md.contains("Get quotes"), md);
    }

    @Test
    void emptyProject_saysNoActions() {
        ws = NamWorkspace.createDefault();
        var p = addProject(ws.getProjectsNodeId(), "Empty");
        assertTrue(ProjectSummary.markdown(ws, p, true).contains("_No actions._"));
    }

    @Test
    void unknownProject_returnsEmpty() {
        ws = NamWorkspace.createDefault();
        assertEquals("", ProjectSummary.markdown(ws, UUID.randomUUID(), true));
    }
}
