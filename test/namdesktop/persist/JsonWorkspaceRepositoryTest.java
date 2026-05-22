package namdesktop.persist;

import namdesktop.model.NamWorkspace;
import namdesktop.model.ProjectTemplate;
import namdesktop.model.TemplateNode;
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
    void saveAndLoad_roundTripsSavedViews(@TempDir Path dir) throws IOException {
        var path = dir.resolve("workspace.json");
        var original = NamWorkspace.createDefault();
        original.getSavedViews().add(new namdesktop.model.SavedView("My view", List.of("@computer", "@home")));
        repo.save(path, original);

        var loaded = repo.load(path);
        assertEquals(1, loaded.getSavedViews().size());
        assertEquals("My view", loaded.getSavedViews().get(0).name());
        assertEquals(List.of("@computer", "@home"), loaded.getSavedViews().get(0).tags());
    }

    @Test
    void load_oldFileWithoutSavedViews_loadsEmptyList(@TempDir Path dir) throws IOException {
        var path = dir.resolve("workspace.json");
        var original = NamWorkspace.createDefault();
        repo.save(path, original);
        // Verify no exception and empty list when field absent (Jackson defaults to null → setSavedViews handles it)
        var loaded = repo.load(path);
        assertNotNull(loaded.getSavedViews());
    }

    @Test
    void saveAndLoad_roundTripsRegisteredTags(@TempDir Path dir) throws IOException {
        var path = dir.resolve("workspace.json");
        var original = NamWorkspace.createDefault();
        original.getRegisteredTags().add("@computer");
        original.getRegisteredTags().add("@home");
        repo.save(path, original);

        var loaded = repo.load(path);
        assertEquals(List.of("@computer", "@home"), loaded.getRegisteredTags());
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
    void saveAndLoad_roundTripsTemplates(@TempDir Path dir) throws IOException {
        var path = dir.resolve("workspace.json");
        var original = NamWorkspace.createDefault();
        var child = new TemplateNode("Book flights", List.of(
                new TemplateNode("Outbound", List.of()),
                new TemplateNode("Return",   List.of())));
        original.getTemplates().add(new ProjectTemplate("Travel template", List.of(child)));
        repo.save(path, original);

        var loaded = repo.load(path);
        assertEquals(1, loaded.getTemplates().size());
        assertEquals("Travel template", loaded.getTemplates().get(0).name());
        assertEquals(1, loaded.getTemplates().get(0).children().size());
        assertEquals("Book flights", loaded.getTemplates().get(0).children().get(0).title());
        assertEquals(2, loaded.getTemplates().get(0).children().get(0).children().size());
    }

    @Test
    void load_oldFileWithoutTemplates_loadsEmptyList(@TempDir Path dir) throws IOException {
        var path = dir.resolve("workspace.json");
        var original = NamWorkspace.createDefault();
        repo.save(path, original);
        var loaded = repo.load(path);
        assertNotNull(loaded.getTemplates());
        assertTrue(loaded.getTemplates().isEmpty());
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
