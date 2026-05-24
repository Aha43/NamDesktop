package namdesktop.lens;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContextLensTest {

    private NamWorkspace workspace;
    private ContextLens lens;

    @BeforeEach
    void setUp() {
        workspace = NamWorkspace.createDefault();
        lens = new ContextLens();
    }

    @Test
    void items_noTagsRequired_returnsAllNextActions() {
        var node = new NamNode(UUID.randomUUID(), "Call dentist");
        node.setStatus(NodeStatus.NEXT);
        workspace.getNodes().put(node.getId(), node);

        assertEquals(1, lens.items(workspace, List.of()).size());
    }

    @Test
    void items_singleTag_returnsMatchingNodes() {
        var match = new NamNode(UUID.randomUUID(), "Write code");
        match.setStatus(NodeStatus.NEXT);
        match.getTags().add("@computer");

        var noMatch = new NamNode(UUID.randomUUID(), "Buy milk");
        noMatch.setStatus(NodeStatus.NEXT);

        workspace.getNodes().put(match.getId(),   match);
        workspace.getNodes().put(noMatch.getId(), noMatch);

        var rows = lens.items(workspace, List.of("@computer"));
        assertEquals(1, rows.size());
        assertEquals("Write code", rows.get(0).title());
    }

    @Test
    void items_multipleTagsAndSemantics_requiresAllTags() {
        var both = new NamNode(UUID.randomUUID(), "Research online");
        both.setStatus(NodeStatus.NEXT);
        both.getTags().add("@computer");
        both.getTags().add("@internet");

        var onlyComputer = new NamNode(UUID.randomUUID(), "Write code");
        onlyComputer.setStatus(NodeStatus.NEXT);
        onlyComputer.getTags().add("@computer");

        workspace.getNodes().put(both.getId(),        both);
        workspace.getNodes().put(onlyComputer.getId(), onlyComputer);

        var rows = lens.items(workspace, List.of("@computer", "@internet"));
        assertEquals(1, rows.size());
        assertEquals("Research online", rows.get(0).title());
    }

    @Test
    void items_excludesDoneNodes() {
        var node = new NamNode(UUID.randomUUID(), "Done thing");
        node.setStatus(NodeStatus.DONE);
        node.getTags().add("@computer");
        workspace.getNodes().put(node.getId(), node);

        assertTrue(lens.items(workspace, List.of("@computer")).isEmpty());
    }

    @Test
    void items_excludesBacklogNodes() {
        var node = new NamNode(UUID.randomUUID(), "Someday thing");
        node.setStatus(NodeStatus.BACKLOG);
        node.getTags().add("@computer");
        workspace.getNodes().put(node.getId(), node);

        assertTrue(lens.items(workspace, List.of("@computer")).isEmpty());
    }

    @Test
    void items_excludesStructuralNodes() {
        workspace.getNode(workspace.getRootNodeId()).orElseThrow()
                .setStatus(NodeStatus.NEXT);

        assertTrue(lens.items(workspace, List.of()).isEmpty());
    }

    @Test
    void items_populatesParentTitle() {
        var project = new NamNode(UUID.randomUUID(), "My Project");
        var action  = new NamNode(UUID.randomUUID(), "Sub task");
        action.setStatus(NodeStatus.NEXT);
        action.getTags().add("@computer");
        workspace.getNodes().put(project.getId(), project);
        workspace.getNodes().put(action.getId(),  action);
        project.getChildIds().add(action.getId());

        var rows = lens.items(workspace, List.of("@computer"));
        assertEquals(1, rows.size());
        assertEquals("My Project", rows.get(0).parentTitle());
    }

    @Test
    void items_matchesActionViaAncestorProjectTag() {
        var project = new NamNode(UUID.randomUUID(), "Trip to Rome");
        project.setProject(true);
        project.getTags().add("@trip");
        var action = new NamNode(UUID.randomUUID(), "Book hotel");
        action.setStatus(NodeStatus.NEXT);
        project.getChildIds().add(action.getId());
        workspace.getNodes().put(project.getId(), project);
        workspace.getNodes().put(action.getId(), action);

        var rows = lens.items(workspace, List.of("@trip"));
        assertEquals(1, rows.size());
        assertEquals("Book hotel", rows.get(0).title());
    }

    @Test
    void items_matchesOnCombinedOwnAndAncestorTags() {
        var project = new NamNode(UUID.randomUUID(), "Project");
        project.setProject(true);
        project.getTags().add("@urgent");
        var action = new NamNode(UUID.randomUUID(), "Do thing");
        action.setStatus(NodeStatus.NEXT);
        action.getTags().add("@phone");
        project.getChildIds().add(action.getId());
        workspace.getNodes().put(project.getId(), project);
        workspace.getNodes().put(action.getId(), action);

        var rows = lens.items(workspace, List.of("@phone", "@urgent"));
        assertEquals(1, rows.size());
    }

    @Test
    void items_includesTags() {
        var node = new NamNode(UUID.randomUUID(), "Task");
        node.setStatus(NodeStatus.NEXT);
        node.getTags().add("@computer");
        node.getTags().add("@home");
        workspace.getNodes().put(node.getId(), node);

        var rows = lens.items(workspace, List.of());
        assertEquals(List.of("@computer", "@home"), rows.get(0).tags());
    }
}
