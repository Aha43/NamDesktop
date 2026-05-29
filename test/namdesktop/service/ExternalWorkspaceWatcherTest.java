package namdesktop.service;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.persist.JsonWorkspaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ExternalWorkspaceWatcherTest {

    @TempDir
    Path dir;

    private Path workspacePath;
    private Path extPath;
    private JsonWorkspaceRepository repo;
    private NamWorkspace base;
    private ExternalWorkspaceWatcher watcher;

    @BeforeEach
    void setUp() throws IOException {
        workspacePath = dir.resolve("workspace.json");
        repo          = new JsonWorkspaceRepository();
        base          = NamWorkspace.createDefault();
        repo.save(workspacePath, base);
        MonitoringMode.enter(workspacePath);
        extPath = MonitoringMode.externalPath(workspacePath);
    }

    @AfterEach
    void tearDown() {
        if (watcher != null) watcher.stop();
    }

    @Test
    void firesCallbackWithCorrectSummaryWhenExternalFileChanges() throws IOException, InterruptedException {
        var latch = new CountDownLatch(1);
        MonitoringMode.DiffSummary[] received = {null};

        var initial = repo.load(extPath);
        watcher = new ExternalWorkspaceWatcher(workspacePath, repo, summary -> {
            received[0] = summary;
            latch.countDown();
        });
        watcher.start(initial);

        addInboxItem(extPath, "Captured thought");

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Callback not fired within 5s");
        assertNotNull(received[0]);
        assertEquals(1, received[0].inboxAdded());
        assertEquals(0, received[0].projectsCreated());
    }

    @Test
    void doesNotFireCallbackWhenExternalFileIsUnparseable() throws IOException, InterruptedException {
        var latch = new CountDownLatch(1);

        var initial = repo.load(extPath);
        watcher = new ExternalWorkspaceWatcher(workspacePath, repo, summary -> latch.countDown());
        watcher.start(initial);

        Files.writeString(extPath, "not valid json {{{");

        assertFalse(latch.await(2, TimeUnit.SECONDS), "Callback must not fire for corrupt file");
    }

    // --- helper ---

    private void addInboxItem(Path path, String title) throws IOException {
        var ws   = repo.load(path);
        var node = new NamNode(UUID.randomUUID(), title);
        node.setStatus(NodeStatus.BACKLOG);
        ws.getNodes().put(node.getId(), node);
        ws.getNode(ws.getInboxNodeId()).orElseThrow().getChildIds().add(node.getId());
        repo.save(path, ws);
    }
}
