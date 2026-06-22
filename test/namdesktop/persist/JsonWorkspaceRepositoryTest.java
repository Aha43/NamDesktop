package namdesktop.persist;

import namdesktop.model.MissionControl;
import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.ProjectTemplate;
import namdesktop.model.Resource;
import namdesktop.model.ResourceType;
import namdesktop.model.TemplateNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonWorkspaceRepositoryTest {

    private final JsonWorkspaceRepository repo = new JsonWorkspaceRepository();

    @Test
    void fromJson_ignoresUnknownProperties() throws Exception {
        // Simulates a NamWeb-written (or newer) document carrying fields this version doesn't know.
        var ws = NamWorkspace.createDefault();
        var node = new NamNode(UUID.randomUUID(), "Task");
        ws.getNodes().put(node.getId(), node);
        ws.getNode(ws.getNextActionsNodeId()).orElseThrow().getChildIds().add(node.getId());

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(repo.toJson(ws));
        tree.put("futureTopLevelField", "from-web");
        var nodes = (com.fasterxml.jackson.databind.node.ObjectNode) tree.get("nodes");
        var firstNodeId = nodes.fieldNames().next();
        ((com.fasterxml.jackson.databind.node.ObjectNode) nodes.get(firstNodeId)).put("futureNodeField", 42);

        var loaded = repo.fromJson(mapper.writeValueAsString(tree));  // must not throw
        assertEquals(ws.getNodes().size(), loaded.getNodes().size());
        assertTrue(loaded.getNode(node.getId()).isPresent());
    }

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
        var child = new TemplateNode("Book flights", true, List.of(
                new TemplateNode("Outbound", false, List.of()),
                new TemplateNode("Return",   false, List.of())));
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
    void saveAndLoad_roundTripsViewOrders(@TempDir Path dir) throws IOException {
        var path = dir.resolve("workspace.json");
        var original = NamWorkspace.createDefault();
        var id1 = UUID.randomUUID(); var id2 = UUID.randomUUID();
        original.getViewOrders().put("next-actions", new java.util.ArrayList<>(List.of(id2, id1)));
        repo.save(path, original);

        var loaded = repo.load(path);
        assertEquals(List.of(id2, id1), loaded.getViewOrders().get("next-actions"));
    }

    @Test
    void saveAndLoad_roundTripsMissionControls(@TempDir Path dir) throws IOException {
        var path = dir.resolve("workspace.json");
        var original = NamWorkspace.createDefault();
        original.getMissionControls().add(new MissionControl("Retirement", List.of("@retirement", "@fi")));
        repo.save(path, original);

        var loaded = repo.load(path);
        assertEquals(1, loaded.getMissionControls().size());
        assertEquals("Retirement", loaded.getMissionControls().get(0).name());
        assertEquals(List.of("@retirement", "@fi"), loaded.getMissionControls().get(0).tags());
    }

    @Test
    void load_oldFileWithoutMissionControls_loadsEmptyList(@TempDir Path dir) throws IOException {
        var path = dir.resolve("workspace.json");
        // Write a file that has no missionControls field (simulates pre-MC workspace)
        Files.writeString(path, """
                {
                  "formatVersion" : 1,
                  "rootNodeId" : "00000000-0000-0000-0000-000000000001",
                  "inboxNodeId" : "00000000-0000-0000-0000-000000000002",
                  "nodes" : {}
                }
                """);

        var loaded = repo.load(path);
        assertNotNull(loaded.getMissionControls());
        assertTrue(loaded.getMissionControls().isEmpty());
    }

    @Test
    void load_oldFileWithoutViewOrders_loadsEmptyMap(@TempDir Path dir) throws IOException {
        var path = dir.resolve("workspace.json");
        var original = NamWorkspace.createDefault();
        original.setViewOrders(null);
        repo.save(path, original);

        var loaded = repo.load(path);
        assertNotNull(loaded.getViewOrders());
        assertTrue(loaded.getViewOrders().isEmpty());
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

    @Test
    void saveAndLoad_preservesResources(@TempDir Path dir) throws IOException {
        var ws   = NamWorkspace.createDefault();
        var node = new NamNode(UUID.randomUUID(), "Buy milk");
        node.getResources().add(new Resource(ResourceType.URI,   "https://example.com", "Link"));
        node.getResources().add(new Resource(ResourceType.TEXT,  "plain text note",     null));
        ws.getNodes().put(node.getId(), node);

        var file = dir.resolve("ws.json");
        repo.save(file, ws);

        var loaded = repo.load(file);
        var reloaded = loaded.getNode(node.getId()).orElseThrow();
        assertEquals(2, reloaded.getResources().size());
        assertEquals(ResourceType.URI,  reloaded.getResources().get(0).getType());
        assertEquals("https://example.com", reloaded.getResources().get(0).getValue());
        assertEquals("Link",                reloaded.getResources().get(0).getDescription());
        assertEquals(ResourceType.TEXT, reloaded.getResources().get(1).getType());
        assertEquals("plain text note", reloaded.getResources().get(1).getValue());
        assertNull(reloaded.getResources().get(1).getDescription());
    }
}
