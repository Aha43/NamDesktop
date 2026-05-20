package namdesktop.persist;

import namdesktop.model.NamWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonWorkspaceRepositoryTest {

    private final JsonWorkspaceRepository repo = new JsonWorkspaceRepository();

    @Test
    void load_returnsFreshDefaultWhenFileAbsent(@TempDir Path dir) throws IOException {
        var ws = repo.load(dir.resolve("missing.json"));
        assertNotNull(ws.getRootNodeId());
        assertNotNull(ws.getInboxNodeId());
        assertNotNull(ws.getProjectsNodeId());
        assertNotNull(ws.getNextActionsNodeId());
    }

    @Test
    void saveAndLoad_roundTripsAllWellKnownIds(@TempDir Path dir) throws IOException {
        var path = dir.resolve("workspace.json");
        var original = NamWorkspace.createDefault();
        repo.save(path, original);

        var loaded = repo.load(path);
        assertEquals(original.getRootNodeId(),        loaded.getRootNodeId());
        assertEquals(original.getInboxNodeId(),       loaded.getInboxNodeId());
        assertEquals(original.getProjectsNodeId(),    loaded.getProjectsNodeId());
        assertEquals(original.getNextActionsNodeId(), loaded.getNextActionsNodeId());
    }

    @Test
    void saveAndLoad_roundTripsTags(@TempDir Path dir) throws IOException {
        var path = dir.resolve("workspace.json");
        var original = NamWorkspace.createDefault();
        var node = original.getNode(original.getInboxNodeId()).orElseThrow();
        node.getTags().add("@computer");
        node.getTags().add("@home");
        repo.save(path, original);

        var loaded = repo.load(path);
        var loadedNode = loaded.getNode(original.getInboxNodeId()).orElseThrow();
        assertEquals(List.of("@computer", "@home"), loadedNode.getTags());
    }

    @Test
    void load_migratesProjectsNodeWhenMissing(@TempDir Path dir) throws IOException {
        var path = dir.resolve("workspace.json");
        Files.writeString(path, """
                {
                  "formatVersion" : 1,
                  "rootNodeId" : "00000000-0000-0000-0000-000000000001",
                  "inboxNodeId" : "00000000-0000-0000-0000-000000000002",
                  "nodes" : {
                    "00000000-0000-0000-0000-000000000001" : {
                      "id" : "00000000-0000-0000-0000-000000000001",
                      "title" : "NAM",
                      "status" : "ACTIVE",
                      "childIds" : ["00000000-0000-0000-0000-000000000002"]
                    },
                    "00000000-0000-0000-0000-000000000002" : {
                      "id" : "00000000-0000-0000-0000-000000000002",
                      "title" : "Inbox",
                      "status" : "ACTIVE",
                      "childIds" : []
                    }
                  }
                }
                """);

        var ws = repo.load(path);
        assertNotNull(ws.getProjectsNodeId());
        var projects = ws.getNode(ws.getProjectsNodeId()).orElseThrow();
        assertEquals("Projects", projects.getTitle());
        assertTrue(ws.getNode(ws.getRootNodeId()).orElseThrow()
                .getChildIds().contains(ws.getProjectsNodeId()));
    }

    @Test
    void load_migratesNextActionsNodeWhenMissing(@TempDir Path dir) throws IOException {
        var path = dir.resolve("workspace.json");
        Files.writeString(path, """
                {
                  "formatVersion" : 1,
                  "rootNodeId" : "00000000-0000-0000-0000-000000000001",
                  "inboxNodeId" : "00000000-0000-0000-0000-000000000002",
                  "nodes" : {
                    "00000000-0000-0000-0000-000000000001" : {
                      "id" : "00000000-0000-0000-0000-000000000001",
                      "title" : "NAM",
                      "status" : "ACTIVE",
                      "childIds" : ["00000000-0000-0000-0000-000000000002"]
                    },
                    "00000000-0000-0000-0000-000000000002" : {
                      "id" : "00000000-0000-0000-0000-000000000002",
                      "title" : "Inbox",
                      "status" : "ACTIVE",
                      "childIds" : []
                    }
                  }
                }
                """);

        var ws = repo.load(path);
        assertNotNull(ws.getNextActionsNodeId());
        var nextActions = ws.getNode(ws.getNextActionsNodeId()).orElseThrow();
        assertEquals("Actions", nextActions.getTitle());
        assertTrue(ws.getNode(ws.getRootNodeId()).orElseThrow()
                .getChildIds().contains(ws.getNextActionsNodeId()));
    }
}
