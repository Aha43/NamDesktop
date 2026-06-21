package namdesktop.ui;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.persist.JsonWorkspaceRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PullOverwriteGuardTest {

    private final JsonWorkspaceRepository repo = new JsonWorkspaceRepository();

    private static void addAction(NamWorkspace ws, String title) {
        var n = new NamNode(UUID.randomUUID(), title);
        ws.getNodes().put(n.getId(), n);
        ws.getNode(ws.getNextActionsNodeId()).orElseThrow().getChildIds().add(n.getId());
    }

    @Test
    void identicalContent_doesNotPrompt() throws Exception {
        var local  = NamWorkspace.createDefault();
        addAction(local, "Task A");
        var remote = repo.fromJson(repo.toJson(local)); // exact copy — pull is a no-op
        assertFalse(MainFrame.pullWouldOverwriteLocal(repo, local, remote));
    }

    @Test
    void localDivergedFromRemote_prompts() throws Exception {
        var local  = NamWorkspace.createDefault();
        addAction(local, "Task A");
        var remote = repo.fromJson(repo.toJson(local));
        addAction(local, "Local-only edit"); // local now has work the remote lacks
        assertTrue(MainFrame.pullWouldOverwriteLocal(repo, local, remote));
    }
}
