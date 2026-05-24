package namdesktop.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NamWorkspaceTest {

    @Test
    void createDefault_rootNodeExists() {
        var ws = NamWorkspace.createDefault();
        assertNotNull(ws.getRootNodeId());
        assertTrue(ws.getNode(ws.getRootNodeId()).isPresent());
    }

    @Test
    void createDefault_rootHasExpectedTitleAndStatus() {
        var ws = NamWorkspace.createDefault();
        var root = ws.getNode(ws.getRootNodeId()).orElseThrow();
        assertEquals("NAM", root.getTitle());
        assertEquals(NodeStatus.BACKLOG, root.getStatus());
    }

    @Test
    void createDefault_rootHasThreeChildren() {
        var ws = NamWorkspace.createDefault();
        var rootChildren = ws.getChildren(ws.getRootNodeId());
        assertEquals(3, rootChildren.size());
        var childIds = rootChildren.stream().map(NamNode::getId).toList();
        assertTrue(childIds.contains(ws.getInboxNodeId()));
        assertTrue(childIds.contains(ws.getProjectsNodeId()));
        assertTrue(childIds.contains(ws.getNextActionsNodeId()));
    }

    @Test
    void getNode_returnsNodeForKnownId() {
        var ws = NamWorkspace.createDefault();
        var rootId = ws.getRootNodeId();
        var result = ws.getNode(rootId);
        assertTrue(result.isPresent());
        assertEquals(rootId, result.get().getId());
    }

    @Test
    void getNode_returnsEmptyForUnknownId() {
        var ws = NamWorkspace.createDefault();
        assertTrue(ws.getNode(UUID.randomUUID()).isEmpty());
    }

    @Test
    void getChildren_returnsChildrenInInsertionOrder() {
        var ws = NamWorkspace.createDefault();
        var parent = new NamNode(UUID.randomUUID(), "Parent");
        var first  = new NamNode(UUID.randomUUID(), "First");
        var second = new NamNode(UUID.randomUUID(), "Second");
        ws.getNodes().put(parent.getId(), parent);
        ws.getNodes().put(first.getId(),  first);
        ws.getNodes().put(second.getId(), second);
        parent.getChildIds().add(first.getId());
        parent.getChildIds().add(second.getId());

        var children = ws.getChildren(parent.getId());
        assertEquals(2, children.size());
        assertEquals("First",  children.get(0).getTitle());
        assertEquals("Second", children.get(1).getTitle());
    }

    @Test
    void getChildren_returnsEmptyListForUnknownParent() {
        var ws = NamWorkspace.createDefault();
        assertTrue(ws.getChildren(UUID.randomUUID()).isEmpty());
    }

    @Test
    void getChildren_skipsDanglingChildIds() {
        var ws = NamWorkspace.createDefault();
        var parent = new NamNode(UUID.randomUUID(), "Parent");
        var real   = new NamNode(UUID.randomUUID(), "Real");
        ws.getNodes().put(parent.getId(), parent);
        ws.getNodes().put(real.getId(),   real);
        parent.getChildIds().add(UUID.randomUUID()); // dangling
        parent.getChildIds().add(real.getId());

        var children = ws.getChildren(parent.getId());
        assertEquals(1, children.size());
        assertEquals("Real", children.get(0).getTitle());
    }

    // --- well-known areas ---

    @Test
    void createDefault_hasInboxNode() {
        var ws = NamWorkspace.createDefault();
        assertNotNull(ws.getInboxNodeId());
        var inbox = ws.getNode(ws.getInboxNodeId()).orElseThrow();
        assertEquals("Inbox", inbox.getTitle());
        assertTrue(ws.getNode(ws.getRootNodeId()).orElseThrow()
                .getChildIds().contains(ws.getInboxNodeId()));
    }

    @Test
    void createDefault_hasProjectsNode() {
        var ws = NamWorkspace.createDefault();
        assertNotNull(ws.getProjectsNodeId());
        var projects = ws.getNode(ws.getProjectsNodeId()).orElseThrow();
        assertEquals("Projects", projects.getTitle());
        assertTrue(ws.getNode(ws.getRootNodeId()).orElseThrow()
                .getChildIds().contains(ws.getProjectsNodeId()));
    }

    @Test
    void createDefault_hasNextActionsNode() {
        var ws = NamWorkspace.createDefault();
        assertNotNull(ws.getNextActionsNodeId());
        var nextActions = ws.getNode(ws.getNextActionsNodeId()).orElseThrow();
        assertEquals("Actions", nextActions.getTitle());
        assertTrue(ws.getNode(ws.getRootNodeId()).orElseThrow()
                .getChildIds().contains(ws.getNextActionsNodeId()));
    }

    // --- savedViews ---

    @Test
    void savedViews_defaultsToEmptyList() {
        var ws = NamWorkspace.createDefault();
        assertNotNull(ws.getSavedViews());
        assertTrue(ws.getSavedViews().isEmpty());
    }

    @Test
    void setSavedViews_withNull_setsEmptyList() {
        var ws = NamWorkspace.createDefault();
        ws.setSavedViews(null);
        assertNotNull(ws.getSavedViews());
        assertTrue(ws.getSavedViews().isEmpty());
    }

    // --- allTags ---

    @Test
    void allTags_returnsEmptyWhenNoNodeHasTags() {
        var ws = NamWorkspace.createDefault();
        assertTrue(ws.allTags().isEmpty());
    }

    @Test
    void allTags_returnsSortedUnionAcrossAllNodes() {
        var ws = NamWorkspace.createDefault();
        var a = new NamNode(UUID.randomUUID(), "A");
        var b = new NamNode(UUID.randomUUID(), "B");
        a.getTags().add("@home");
        a.getTags().add("@computer");
        b.getTags().add("@computer");
        b.getTags().add("@errands");
        ws.getNodes().put(a.getId(), a);
        ws.getNodes().put(b.getId(), b);

        var tags = ws.allTags();
        assertEquals(List.of("@computer", "@errands", "@home"), tags);
    }

    // --- tags ---

    @Test
    void newNode_hasEmptyTagList() {
        var node = new NamNode(UUID.randomUUID(), "Test");
        assertNotNull(node.getTags());
        assertTrue(node.getTags().isEmpty());
    }

    @Test
    void setTags_withNull_setsEmptyList() {
        var node = new NamNode(UUID.randomUUID(), "Test");
        node.setTags(null);
        assertNotNull(node.getTags());
        assertTrue(node.getTags().isEmpty());
    }

    // --- buildPath ---

    @Test
    void buildPath_returnsEmptyForUnknownNode() {
        var ws = NamWorkspace.createDefault();
        assertTrue(ws.buildPath(UUID.randomUUID()).isEmpty());
    }

    @Test
    void buildPath_returnsSingleNodeForRoot() {
        var ws = NamWorkspace.createDefault();
        var path = ws.buildPath(ws.getRootNodeId());
        assertEquals(1, path.size());
        assertEquals(ws.getRootNodeId(), path.get(0).getId());
    }

    @Test
    void buildPath_returnsPathFromRootToDirectChild() {
        var ws = NamWorkspace.createDefault();
        var path = ws.buildPath(ws.getInboxNodeId());
        assertEquals(2, path.size());
        assertEquals(ws.getRootNodeId(),  path.get(0).getId());
        assertEquals(ws.getInboxNodeId(), path.get(1).getId());
    }

    @Test
    void buildPath_returnsFullPathForDeepNode() {
        var ws     = NamWorkspace.createDefault();
        var parent = new NamNode(UUID.randomUUID(), "Parent");
        var child  = new NamNode(UUID.randomUUID(), "Child");
        ws.getNodes().put(parent.getId(), parent);
        ws.getNodes().put(child.getId(),  child);
        ws.getNode(ws.getProjectsNodeId()).orElseThrow().getChildIds().add(parent.getId());
        parent.getChildIds().add(child.getId());

        var path = ws.buildPath(child.getId());
        assertEquals(4, path.size()); // root > Projects > Parent > Child
        assertEquals("NAM",      path.get(0).getTitle());
        assertEquals("Projects", path.get(1).getTitle());
        assertEquals("Parent",   path.get(2).getTitle());
        assertEquals("Child",    path.get(3).getTitle());
    }

    // --- getParent ---

    @Test
    void getParent_returnsParentForKnownChild() {
        var ws = NamWorkspace.createDefault();
        var parent = new NamNode(UUID.randomUUID(), "Parent");
        var child  = new NamNode(UUID.randomUUID(), "Child");
        ws.getNodes().put(parent.getId(), parent);
        ws.getNodes().put(child.getId(),  child);
        parent.getChildIds().add(child.getId());

        var result = ws.getParent(child.getId());
        assertTrue(result.isPresent());
        assertEquals("Parent", result.get().getTitle());
    }

    @Test
    void getParent_returnsEmptyForRootNode() {
        var ws = NamWorkspace.createDefault();
        assertTrue(ws.getParent(ws.getRootNodeId()).isEmpty());
    }

    @Test
    void getParent_returnsEmptyForOrphanNode() {
        var ws = NamWorkspace.createDefault();
        var orphan = new NamNode(UUID.randomUUID(), "Orphan");
        ws.getNodes().put(orphan.getId(), orphan);

        assertTrue(ws.getParent(orphan.getId()).isEmpty());
    }

    @Test
    void getInboxItems_returnsEmptyInitially() {
        var ws = NamWorkspace.createDefault();
        assertTrue(ws.getInboxItems().isEmpty());
    }

    @Test
    void getInboxItems_returnsItemsInInsertionOrder() {
        var ws = NamWorkspace.createDefault();
        var first  = new NamNode(UUID.randomUUID(), "First");
        var second = new NamNode(UUID.randomUUID(), "Second");
        ws.getNodes().put(first.getId(),  first);
        ws.getNodes().put(second.getId(), second);
        ws.getNode(ws.getInboxNodeId()).orElseThrow().getChildIds().add(first.getId());
        ws.getNode(ws.getInboxNodeId()).orElseThrow().getChildIds().add(second.getId());

        var items = ws.getInboxItems();
        assertEquals(2, items.size());
        assertEquals("First",  items.get(0).getTitle());
        assertEquals("Second", items.get(1).getTitle());
    }

    @Test
    void effectiveTags_includesOwnTags() {
        var ws = NamWorkspace.createDefault();
        var action = new NamNode(UUID.randomUUID(), "Call dentist");
        action.getTags().add("@phone");
        ws.getNodes().put(action.getId(), action);

        assertTrue(ws.effectiveTags(action.getId()).contains("@phone"));
    }

    @Test
    void effectiveTags_includesParentProjectTags() {
        var ws = NamWorkspace.createDefault();
        var project = new NamNode(UUID.randomUUID(), "Trip to Rome");
        project.setProject(true);
        project.getTags().add("@trip");
        var action = new NamNode(UUID.randomUUID(), "Book hotel");
        project.getChildIds().add(action.getId());
        ws.getNodes().put(project.getId(), project);
        ws.getNodes().put(action.getId(), action);

        var tags = ws.effectiveTags(action.getId());
        assertTrue(tags.contains("@trip"));
    }

    @Test
    void effectiveTags_includesMultiLevelAncestorTags() {
        var ws = NamWorkspace.createDefault();
        var projectA = new NamNode(UUID.randomUUID(), "Project A");
        projectA.setProject(true);
        projectA.getTags().add("@urgent");
        var projectB = new NamNode(UUID.randomUUID(), "Project B");
        projectB.setProject(true);
        projectB.getTags().add("@home");
        projectA.getChildIds().add(projectB.getId());
        var action = new NamNode(UUID.randomUUID(), "Do thing");
        action.getTags().add("@car");
        projectB.getChildIds().add(action.getId());
        ws.getNodes().put(projectA.getId(), projectA);
        ws.getNodes().put(projectB.getId(), projectB);
        ws.getNodes().put(action.getId(), action);

        var tags = ws.effectiveTags(action.getId());
        assertTrue(tags.contains("@car"));
        assertTrue(tags.contains("@home"));
        assertTrue(tags.contains("@urgent"));
    }

    @Test
    void effectiveTags_doesNotIncludeNonProjectAncestorTags() {
        var ws = NamWorkspace.createDefault();
        var nonProject = new NamNode(UUID.randomUUID(), "Some container");
        nonProject.getTags().add("@shouldNotInherit");
        var action = new NamNode(UUID.randomUUID(), "Action");
        nonProject.getChildIds().add(action.getId());
        ws.getNodes().put(nonProject.getId(), nonProject);
        ws.getNodes().put(action.getId(), action);

        assertFalse(ws.effectiveTags(action.getId()).contains("@shouldNotInherit"));
    }
}
