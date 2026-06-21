package namdesktop.lens;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProjectsLensTest {

    private NamWorkspace workspace;
    private ProjectsLens lens;

    @BeforeEach
    void setUp() {
        workspace = NamWorkspace.createDefault();
        lens = new ProjectsLens();
    }

    @Test
    void items_returnsEmptyListWhenNoProjects() {
        assertTrue(lens.items(workspace).isEmpty());
    }

    @Test
    void items_projectsTitleAndStatus() {
        var node = new NamNode(UUID.randomUUID(), "Website redesign");
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(workspace.getProjectsNodeId()).orElseThrow()
                .getChildIds().add(node.getId());

        var rows = lens.items(workspace);
        assertEquals(1, rows.size());
        assertEquals("Website redesign", rows.get(0).title());
        assertEquals(NodeStatus.BACKLOG, rows.get(0).status());
        assertEquals(node.getId(),       rows.get(0).id());
    }

    @Test
    void items_reflectsDoneStatus() {
        var node = new NamNode(UUID.randomUUID(), "Done project");
        node.setStatus(NodeStatus.DONE);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(workspace.getProjectsNodeId()).orElseThrow()
                .getChildIds().add(node.getId());

        var rows = lens.items(workspace);
        assertEquals(NodeStatus.DONE, rows.get(0).status());
    }

    @Test
    void items_excludesArchivedByDefault() {
        addProject("Active", NodeStatus.BACKLOG);
        addProject("Archived", NodeStatus.ARCHIVED);
        var rows = lens.items(workspace);
        assertEquals(1, rows.size());
        assertEquals("Active", rows.get(0).title());
    }

    @Test
    void items_includesArchivedWhenRequested() {
        addProject("Active", NodeStatus.BACKLOG);
        addProject("Archived", NodeStatus.ARCHIVED);
        assertEquals(2, lens.items(workspace, java.util.List.of(), true).size());
    }

    private void addProject(String title, NodeStatus status) {
        var n = new NamNode(UUID.randomUUID(), title);
        n.setStatus(status);
        workspace.getNodes().put(n.getId(), n);
        workspace.getNode(workspace.getProjectsNodeId()).orElseThrow().getChildIds().add(n.getId());
    }

    @Test
    void items_preservesOrder() {
        var first  = new NamNode(UUID.randomUUID(), "First");
        var second = new NamNode(UUID.randomUUID(), "Second");
        workspace.getNodes().put(first.getId(),  first);
        workspace.getNodes().put(second.getId(), second);
        var projectsChildIds = workspace.getNode(workspace.getProjectsNodeId())
                .orElseThrow().getChildIds();
        projectsChildIds.add(first.getId());
        projectsChildIds.add(second.getId());

        var rows = lens.items(workspace);
        assertEquals(2,        rows.size());
        assertEquals("First",  rows.get(0).title());
        assertEquals("Second", rows.get(1).title());
    }
}
