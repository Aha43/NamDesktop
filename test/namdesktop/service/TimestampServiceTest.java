package namdesktop.service;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.persist.JsonWorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TimestampServiceTest {

    @TempDir Path dir;

    private NamWorkspace workspace;
    private JsonWorkspaceRepository repository;
    private NamWorkspaceService service;
    private Path wsPath;

    @BeforeEach
    void setUp() {
        workspace  = NamWorkspace.createDefault();
        repository = new JsonWorkspaceRepository();
        wsPath     = dir.resolve("workspace.json");
        service    = new NamWorkspaceService(workspace, repository, wsPath);
    }

    // --- creation ---

    @Test
    void addChild_setsCreatedAtAndUpdatedAt() throws IOException {
        var id = service.addChild(workspace.getInboxNodeId(), "Buy milk");
        var node = workspace.getNode(id).orElseThrow();
        assertNotNull(node.getCreatedAt());
        assertNotNull(node.getUpdatedAt());
        assertEquals(node.getCreatedAt(), node.getUpdatedAt());
    }

    @Test
    void addSubProject_setsCreatedAtAndUpdatedAt() throws IOException {
        var id = service.addSubProject(workspace.getProjectsNodeId(), "Trip");
        var node = workspace.getNode(id).orElseThrow();
        assertNotNull(node.getCreatedAt());
        assertNotNull(node.getUpdatedAt());
    }

    @Test
    void createNextAction_setsCreatedAtAndUpdatedAt() throws IOException {
        var id = service.createNextAction("Call dentist");
        var node = workspace.getNode(id).orElseThrow();
        assertNotNull(node.getCreatedAt());
        assertNotNull(node.getUpdatedAt());
    }

    // --- createdAt is immutable after creation ---

    @Test
    void createdAt_unchangedByRename() throws IOException {
        var id = service.addChild(workspace.getInboxNodeId(), "Draft");
        var created = workspace.getNode(id).orElseThrow().getCreatedAt();
        service.renameNode(id, "Draft v2");
        assertEquals(created, workspace.getNode(id).orElseThrow().getCreatedAt());
    }

    @Test
    void createdAt_unchangedByStatusChange() throws IOException {
        var id = service.createNextAction("Call bank");
        var created = workspace.getNode(id).orElseThrow().getCreatedAt();
        service.markDone(id);
        assertEquals(created, workspace.getNode(id).orElseThrow().getCreatedAt());
    }

    // --- updatedAt advances on mutations ---

    @Test
    void renameNode_advancesUpdatedAt() throws IOException {
        var id = service.addChild(workspace.getInboxNodeId(), "Old title");
        var before = workspace.getNode(id).orElseThrow().getUpdatedAt();
        service.renameNode(id, "New title");
        var after = workspace.getNode(id).orElseThrow().getUpdatedAt();
        assertNotNull(after);
        assertFalse(after.isBefore(before));
    }

    @Test
    void updateDescription_advancesUpdatedAt() throws IOException {
        var id = service.addChild(workspace.getInboxNodeId(), "Item");
        var before = workspace.getNode(id).orElseThrow().getUpdatedAt();
        service.updateDescription(id, "some details");
        var after = workspace.getNode(id).orElseThrow().getUpdatedAt();
        assertFalse(after.isBefore(before));
    }

    @Test
    void updateTags_advancesUpdatedAt() throws IOException {
        var id = service.addChild(workspace.getInboxNodeId(), "Item");
        var before = workspace.getNode(id).orElseThrow().getUpdatedAt();
        service.updateTags(id, java.util.List.of("@computer"));
        var after = workspace.getNode(id).orElseThrow().getUpdatedAt();
        assertFalse(after.isBefore(before));
    }

    // --- statusChangedAt set only on status transitions ---

    @Test
    void markDone_setsStatusChangedAtAndUpdatedAt() throws IOException {
        var id = service.createNextAction("Send email");
        service.markDone(id);
        var node = workspace.getNode(id).orElseThrow();
        assertNotNull(node.getStatusChangedAt());
        assertNotNull(node.getUpdatedAt());
    }

    @Test
    void renameNode_doesNotSetStatusChangedAt() throws IOException {
        var id = service.createNextAction("Send email");
        assertNull(workspace.getNode(id).orElseThrow().getStatusChangedAt());
        service.renameNode(id, "Send email today");
        assertNull(workspace.getNode(id).orElseThrow().getStatusChangedAt());
    }

    @Test
    void updateTags_doesNotSetStatusChangedAt() throws IOException {
        var id = service.createNextAction("Send email");
        service.updateTags(id, java.util.List.of("@computer"));
        assertNull(workspace.getNode(id).orElseThrow().getStatusChangedAt());
    }

    // --- touchNode ---

    @Test
    void touchNode_advancesUpdatedAt() throws IOException {
        var id = service.addChild(workspace.getInboxNodeId(), "Item");
        var before = workspace.getNode(id).orElseThrow().getUpdatedAt();
        service.touchNode(id);
        var after = workspace.getNode(id).orElseThrow().getUpdatedAt();
        assertFalse(after.isBefore(before));
    }

    @Test
    void touchNode_doesNotSetStatusChangedAt() throws IOException {
        var id = service.addChild(workspace.getInboxNodeId(), "Item");
        service.touchNode(id);
        assertNull(workspace.getNode(id).orElseThrow().getStatusChangedAt());
    }

    @Test
    void touchNode_noopForUnknownId() {
        assertDoesNotThrow(() -> service.touchNode(UUID.randomUUID()));
    }

    // --- persistence round-trip ---

    @Test
    void timestamps_surviveRoundTrip() throws IOException {
        var id = service.addChild(workspace.getInboxNodeId(), "Buy milk");
        service.markDone(id);

        var loaded = repository.load(wsPath);
        var node   = loaded.getNode(id).orElseThrow();
        assertNotNull(node.getCreatedAt());
        assertNotNull(node.getUpdatedAt());
        assertNotNull(node.getStatusChangedAt());
    }

    @Test
    void oldJsonWithoutTimestamps_loadsWithNulls(@TempDir Path tmpDir) throws IOException {
        var path = tmpDir.resolve("workspace.json");
        java.nio.file.Files.writeString(path, """
                {
                  "formatVersion" : 1,
                  "rootNodeId" : "00000000-0000-0000-0000-000000000001",
                  "inboxNodeId" : "00000000-0000-0000-0000-000000000002",
                  "nodes" : {
                    "00000000-0000-0000-0000-000000000002" : {
                      "id" : "00000000-0000-0000-0000-000000000002",
                      "title" : "Inbox",
                      "status" : "ACTIVE",
                      "childIds" : []
                    }
                  }
                }
                """);

        var repo = new JsonWorkspaceRepository();
        var loaded = repo.load(path);
        var node = loaded.getNode(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000002")).orElseThrow();
        assertNull(node.getCreatedAt());
        assertNull(node.getUpdatedAt());
        assertNull(node.getStatusChangedAt());
    }
}
