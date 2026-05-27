package namdesktop.service;

import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.persist.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PrerequisiteServiceTest {

    private NamWorkspace workspace;
    private NamWorkspaceService service;
    private UUID actionsId;

    @BeforeEach
    void setUp() {
        workspace  = NamWorkspace.createDefault();
        service    = new NamWorkspaceService(workspace, new InMemoryRepository(), Path.of("unused"));
        actionsId  = workspace.getNextActionsNodeId();
    }

    // --- addPrerequisite ---

    @Test
    void addPrerequisite_linksBlockedByField() throws IOException {
        var a = service.createNextAction("A");
        var b = service.createNextAction("B");
        assertTrue(service.addPrerequisite(a, b));
        assertTrue(workspace.getNode(a).orElseThrow().getBlockedBy().contains(b));
    }

    @Test
    void addPrerequisite_returnsFalseForSelf() throws IOException {
        var a = service.createNextAction("A");
        assertFalse(service.addPrerequisite(a, a));
    }

    @Test
    void addPrerequisite_idempotent() throws IOException {
        var a = service.createNextAction("A");
        var b = service.createNextAction("B");
        service.addPrerequisite(a, b);
        service.addPrerequisite(a, b);
        assertEquals(1, workspace.getNode(a).orElseThrow().getBlockedBy().size());
    }

    // --- cycle detection ---

    @Test
    void addPrerequisite_returnsFalseOnDirectCycle() throws IOException {
        var a = service.createNextAction("A");
        var b = service.createNextAction("B");
        assertTrue(service.addPrerequisite(a, b));
        assertFalse(service.addPrerequisite(b, a));
    }

    @Test
    void addPrerequisite_returnsFalseOnTransitiveCycle() throws IOException {
        var a = service.createNextAction("A");
        var b = service.createNextAction("B");
        var c = service.createNextAction("C");
        service.addPrerequisite(a, b); // a blocked by b
        service.addPrerequisite(b, c); // b blocked by c
        assertFalse(service.addPrerequisite(c, a)); // would make c blocked by a → cycle
    }

    // --- removePrerequisite ---

    @Test
    void removePrerequisite_removesLink() throws IOException {
        var a = service.createNextAction("A");
        var b = service.createNextAction("B");
        service.addPrerequisite(a, b);
        service.removePrerequisite(a, b);
        assertFalse(workspace.getNode(a).orElseThrow().getBlockedBy().contains(b));
    }

    @Test
    void removePrerequisite_noopIfNotLinked() throws IOException {
        var a = service.createNextAction("A");
        var b = service.createNextAction("B");
        assertDoesNotThrow(() -> service.removePrerequisite(a, b));
    }

    // --- isBlocked ---

    @Test
    void isBlocked_trueWhenPrereqNotDone() throws IOException {
        var a = service.createNextAction("A");
        var b = service.createNextAction("B");
        service.addPrerequisite(a, b);
        assertTrue(service.isBlocked(a));
    }

    @Test
    void isBlocked_falseWhenPrereqDone() throws IOException {
        var a = service.createNextAction("A");
        var b = service.createNextAction("B");
        service.addPrerequisite(a, b);
        service.markDone(b);
        assertFalse(service.isBlocked(a));
    }

    @Test
    void isBlocked_falseWithNoPrereqs() throws IOException {
        var a = service.createNextAction("A");
        assertFalse(service.isBlocked(a));
    }

    @Test
    void isBlocked_trueWhenAnyPrereqNotDone() throws IOException {
        var a = service.createNextAction("A");
        var b = service.createNextAction("B");
        var c = service.createNextAction("C");
        service.addPrerequisite(a, b);
        service.addPrerequisite(a, c);
        service.markDone(b);
        assertTrue(service.isBlocked(a)); // c still not done
    }

    // --- unblocks ---

    @Test
    void unblocks_returnsActionsBlockedByPrereq() throws IOException {
        var a = service.createNextAction("A");
        var b = service.createNextAction("B");
        var c = service.createNextAction("C");
        service.addPrerequisite(b, a);
        service.addPrerequisite(c, a);
        var result = service.unblocks(a);
        assertTrue(result.contains(b));
        assertTrue(result.contains(c));
        assertEquals(2, result.size());
    }

    @Test
    void unblocks_emptyWhenNothingBlocked() throws IOException {
        var a = service.createNextAction("A");
        assertTrue(service.unblocks(a).isEmpty());
    }

    // --- auto-cleanup on delete ---

    @Test
    void deleteLeaf_sweepsBlockedByReferences() throws IOException {
        var a = service.createNextAction("A");
        var b = service.createNextAction("B");
        service.addPrerequisite(a, b);
        service.deleteLeaf(b);
        assertFalse(workspace.getNode(a).orElseThrow().getBlockedBy().contains(b));
    }

    @Test
    void deleteRecursive_sweepsBlockedByReferences() throws IOException {
        var projectsId = workspace.getProjectsNodeId();
        var proj = service.addChild(projectsId, "Project");
        var b    = service.addChild(proj, "B-action");
        var a    = service.createNextAction("A");
        service.addPrerequisite(a, b);
        service.deleteRecursive(proj);
        assertFalse(workspace.getNode(a).orElseThrow().getBlockedBy().contains(b));
    }

    // --- in-memory repo ---

    private static final class InMemoryRepository implements WorkspaceRepository {
        @Override public NamWorkspace load(Path p) { return NamWorkspace.createDefault(); }
        @Override public void save(Path p, NamWorkspace w) {}
    }
}
