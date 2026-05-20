package namdesktop.lens;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SearchLensTest {

    private NamWorkspace workspace;
    private SearchLens lens;

    @BeforeEach
    void setUp() {
        workspace = NamWorkspace.createDefault();
        lens = new SearchLens();
    }

    @Test
    void search_emptyQuery_returnsEmpty() {
        assertTrue(lens.search(workspace, "").isEmpty());
    }

    @Test
    void search_blankQuery_returnsEmpty() {
        assertTrue(lens.search(workspace, "   ").isEmpty());
    }

    @Test
    void search_nullQuery_returnsEmpty() {
        assertTrue(lens.search(workspace, null).isEmpty());
    }

    @Test
    void search_titleMatch_caseInsensitive() {
        var node = new NamNode(UUID.randomUUID(), "Call Dentist");
        node.setStatus(NodeStatus.NEXT);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(workspace.getNextActionsNodeId()).orElseThrow().getChildIds().add(node.getId());

        var results = lens.search(workspace, "dentist");
        assertEquals(1, results.size());
        assertEquals("Call Dentist", results.get(0).title());
    }

    @Test
    void search_partialTitleMatch() {
        var node = new NamNode(UUID.randomUUID(), "Buy groceries");
        node.setStatus(NodeStatus.NEXT);
        workspace.getNodes().put(node.getId(), node);

        assertEquals(1, lens.search(workspace, "grocer").size());
    }

    @Test
    void search_tagMatch() {
        var node = new NamNode(UUID.randomUUID(), "Write report");
        node.setStatus(NodeStatus.NEXT);
        node.getTags().add("@computer");
        workspace.getNodes().put(node.getId(), node);

        var results = lens.search(workspace, "@computer");
        assertEquals(1, results.size());
        assertEquals("Write report", results.get(0).title());
    }

    @Test
    void search_tagMatch_caseInsensitive() {
        var node = new NamNode(UUID.randomUUID(), "Task");
        node.setStatus(NodeStatus.NEXT);
        node.getTags().add("@computer");
        workspace.getNodes().put(node.getId(), node);

        assertEquals(1, lens.search(workspace, "@COMPUTER").size());
    }

    @Test
    void search_excludesDoneNodes() {
        var node = new NamNode(UUID.randomUUID(), "Done task");
        node.setStatus(NodeStatus.DONE);
        workspace.getNodes().put(node.getId(), node);

        assertTrue(lens.search(workspace, "done task").isEmpty());
    }

    @Test
    void search_excludesStructuralNodes() {
        workspace.getNode(workspace.getRootNodeId()).orElseThrow().setStatus(NodeStatus.NEXT);
        workspace.getNode(workspace.getInboxNodeId()).orElseThrow().setStatus(NodeStatus.NEXT);

        assertTrue(lens.search(workspace, "inbox").isEmpty());
        assertTrue(lens.search(workspace, "nam").isEmpty());
    }

    @Test
    void search_typeIsInbox_forInboxChild() {
        var node = new NamNode(UUID.randomUUID(), "Buy milk");
        node.setStatus(NodeStatus.BACKLOG);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(workspace.getInboxNodeId()).orElseThrow().getChildIds().add(node.getId());

        var results = lens.search(workspace, "buy milk");
        assertEquals(1, results.size());
        assertEquals("Inbox", results.get(0).type());
    }

    @Test
    void search_typeIsProject_forProjectsAreaChild() {
        var node = new NamNode(UUID.randomUUID(), "Build shed");
        node.setStatus(NodeStatus.NEXT);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(workspace.getProjectsNodeId()).orElseThrow().getChildIds().add(node.getId());

        var results = lens.search(workspace, "build shed");
        assertEquals(1, results.size());
        assertEquals("Project", results.get(0).type());
    }

    @Test
    void search_typeIsAction_forNextNode() {
        var node = new NamNode(UUID.randomUUID(), "Call bank");
        node.setStatus(NodeStatus.NEXT);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(workspace.getNextActionsNodeId()).orElseThrow().getChildIds().add(node.getId());

        var results = lens.search(workspace, "call bank");
        assertEquals(1, results.size());
        assertEquals("Action", results.get(0).type());
    }

    @Test
    void search_typeIsBacklog_forBacklogNode() {
        var node = new NamNode(UUID.randomUUID(), "Learn piano");
        node.setStatus(NodeStatus.BACKLOG);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(workspace.getNextActionsNodeId()).orElseThrow().getChildIds().add(node.getId());

        var results = lens.search(workspace, "learn piano");
        assertEquals(1, results.size());
        assertEquals("Backlog", results.get(0).type());
    }

    @Test
    void search_parentTitlePopulated_forActionUnderProject() {
        var project = new NamNode(UUID.randomUUID(), "Home Reno");
        var action  = new NamNode(UUID.randomUUID(), "Buy paint");
        action.setStatus(NodeStatus.NEXT);
        workspace.getNodes().put(project.getId(), project);
        workspace.getNodes().put(action.getId(),  action);
        workspace.getNode(workspace.getProjectsNodeId()).orElseThrow().getChildIds().add(project.getId());
        project.getChildIds().add(action.getId());

        var results = lens.search(workspace, "buy paint");
        assertEquals(1, results.size());
        assertEquals("Home Reno", results.get(0).parentTitle());
    }

    @Test
    void search_resultsOrderedInboxActionProjectBacklog() {
        var inbox   = new NamNode(UUID.randomUUID(), "widget");
        inbox.setStatus(NodeStatus.BACKLOG);
        workspace.getNodes().put(inbox.getId(), inbox);
        workspace.getNode(workspace.getInboxNodeId()).orElseThrow().getChildIds().add(inbox.getId());

        var backlog = new NamNode(UUID.randomUUID(), "widget");
        backlog.setStatus(NodeStatus.BACKLOG);
        workspace.getNodes().put(backlog.getId(), backlog);
        workspace.getNode(workspace.getNextActionsNodeId()).orElseThrow().getChildIds().add(backlog.getId());

        var project = new NamNode(UUID.randomUUID(), "widget");
        project.setStatus(NodeStatus.NEXT);
        workspace.getNodes().put(project.getId(), project);
        workspace.getNode(workspace.getProjectsNodeId()).orElseThrow().getChildIds().add(project.getId());

        var action  = new NamNode(UUID.randomUUID(), "widget");
        action.setStatus(NodeStatus.NEXT);
        workspace.getNodes().put(action.getId(), action);
        workspace.getNode(workspace.getNextActionsNodeId()).orElseThrow().getChildIds().add(action.getId());

        var results = lens.search(workspace, "widget");
        assertEquals(4, results.size());
        assertEquals("Inbox",   results.get(0).type());
        assertEquals("Action",  results.get(1).type());
        assertEquals("Project", results.get(2).type());
        assertEquals("Backlog", results.get(3).type());
    }
}
