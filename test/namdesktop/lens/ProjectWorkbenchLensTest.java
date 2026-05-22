package namdesktop.lens;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProjectWorkbenchLensTest {

    private NamWorkspace workspace;
    private ProjectWorkbenchLens lens;

    @BeforeEach
    void setUp() {
        workspace = NamWorkspace.createDefault();
        lens = new ProjectWorkbenchLens();
    }

    // helpers

    private UUID addProject(String title) {
        var node = new NamNode(UUID.randomUUID(), title);
        node.setProject(true);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(workspace.getProjectsNodeId()).orElseThrow().getChildIds().add(node.getId());
        return node.getId();
    }

    private UUID addSubProject(UUID parentId, String title) {
        var node = new NamNode(UUID.randomUUID(), title);
        node.setProject(true);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(parentId).orElseThrow().getChildIds().add(node.getId());
        return node.getId();
    }

    private UUID addAction(UUID parentId, String title) {
        var node = new NamNode(UUID.randomUUID(), title);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(parentId).orElseThrow().getChildIds().add(node.getId());
        return node.getId();
    }

    private UUID addDoneAction(UUID parentId, String title) {
        var node = new NamNode(UUID.randomUUID(), title);
        node.setStatus(NodeStatus.DONE);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(parentId).orElseThrow().getChildIds().add(node.getId());
        return node.getId();
    }

    // --- breadcrumb ---

    @Test
    void breadcrumb_singleTopLevelProject() {
        var projectId = addProject("Travel");
        var p = lens.project(workspace, projectId);
        assertEquals(1, p.breadcrumb().size());
        assertEquals("Travel", p.breadcrumb().get(0).getTitle());
    }

    @Test
    void breadcrumb_subProjectIncludesParent() {
        var parentId = addProject("Travel");
        var childId  = addSubProject(parentId, "Japan");
        var p = lens.project(workspace, childId);
        assertEquals(2, p.breadcrumb().size());
        assertEquals("Travel", p.breadcrumb().get(0).getTitle());
        assertEquals("Japan",  p.breadcrumb().get(1).getTitle());
    }

    @Test
    void breadcrumb_deepPathIsCorrectlyOrdered() {
        var a = addProject("A");
        var b = addSubProject(a, "B");
        var c = addSubProject(b, "C");
        var p = lens.project(workspace, c);
        assertEquals(3, p.breadcrumb().size());
        assertEquals("A", p.breadcrumb().get(0).getTitle());
        assertEquals("B", p.breadcrumb().get(1).getTitle());
        assertEquals("C", p.breadcrumb().get(2).getTitle());
    }

    @Test
    void breadcrumb_doesNotIncludeProjectsAreaNode() {
        var projectId = addProject("Travel");
        var p = lens.project(workspace, projectId);
        assertTrue(p.breadcrumb().stream()
                .noneMatch(n -> n.getId().equals(workspace.getProjectsNodeId())));
    }

    // --- directActions ---

    @Test
    void directActions_emptyWhenNoChildren() {
        var projectId = addProject("Travel");
        var p = lens.project(workspace, projectId);
        assertTrue(p.directActions().isEmpty());
    }

    @Test
    void directActions_includesActionChildren() {
        var projectId = addProject("Travel");
        addAction(projectId, "Book flights");
        addAction(projectId, "Book hotel");
        var p = lens.project(workspace, projectId);
        assertEquals(2, p.directActions().size());
    }

    @Test
    void directActions_excludesSubProjects() {
        var projectId = addProject("Travel");
        addAction(projectId, "Book flights");
        addSubProject(projectId, "Accommodation");
        var p = lens.project(workspace, projectId);
        assertEquals(1, p.directActions().size());
        assertEquals("Book flights", p.directActions().get(0).getTitle());
    }

    @Test
    void directActions_includesDoneActions() {
        var projectId = addProject("Travel");
        addAction(projectId, "Book flights");
        addDoneAction(projectId, "Get passport");
        var p = lens.project(workspace, projectId);
        assertEquals(2, p.directActions().size());
    }

    // --- childSections ---

    @Test
    void childSections_emptyWhenNoSubProjects() {
        var projectId = addProject("Travel");
        addAction(projectId, "Book flights");
        var p = lens.project(workspace, projectId);
        assertTrue(p.childSections().isEmpty());
    }

    @Test
    void childSections_onePerDirectChildProject() {
        var projectId = addProject("Travel");
        addSubProject(projectId, "Flights");
        addSubProject(projectId, "Hotels");
        var p = lens.project(workspace, projectId);
        assertEquals(2, p.childSections().size());
    }

    @Test
    void childSections_containsDirectActionsOfChild() {
        var projectId  = addProject("Travel");
        var flightsId  = addSubProject(projectId, "Flights");
        addAction(flightsId, "Book outbound");
        addAction(flightsId, "Book return");
        var p = lens.project(workspace, projectId);
        assertEquals(1, p.childSections().size());
        assertEquals(2, p.childSections().get(0).directActions().size());
    }

    @Test
    void childSections_doesNotExpandGrandchildProjects() {
        var projectId   = addProject("Travel");
        var flightsId   = addSubProject(projectId, "Flights");
        var grandchildId = addSubProject(flightsId, "Layovers");
        addAction(grandchildId, "Arrange transfer");
        var p = lens.project(workspace, projectId);
        // grandchild project's actions do not appear in Flights section
        assertEquals(0, p.childSections().get(0).directActions().size());
    }

    @Test
    void childSections_grandchildProjectNotExpandedAsSection() {
        var projectId   = addProject("Travel");
        var flightsId   = addSubProject(projectId, "Flights");
        addSubProject(flightsId, "Layovers");
        var p = lens.project(workspace, projectId);
        // only direct child projects become sections
        assertEquals(1, p.childSections().size());
        assertEquals("Flights", p.childSections().get(0).project().getTitle());
    }
}
