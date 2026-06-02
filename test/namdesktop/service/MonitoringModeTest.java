package namdesktop.service;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.model.Resource;
import namdesktop.model.ResourceType;
import namdesktop.persist.JsonWorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MonitoringModeTest {

    @TempDir
    Path dir;

    private Path workspacePath;
    private JsonWorkspaceRepository repo;
    private NamWorkspace base;

    @BeforeEach
    void setUp() throws IOException {
        workspacePath = dir.resolve("workspace.json");
        repo = new JsonWorkspaceRepository();
        base = NamWorkspace.createDefault();
        repo.save(workspacePath, base);
    }

    // --- isActive ---

    @Test
    void isActive_falseWhenNoSentinel() {
        assertFalse(MonitoringMode.isActive(workspacePath));
    }

    @Test
    void isActive_trueAfterEnter() throws IOException {
        MonitoringMode.enter(workspacePath);
        assertTrue(MonitoringMode.isActive(workspacePath));
    }

    @Test
    void isActive_falseForNullPath() {
        assertFalse(MonitoringMode.isActive(null));
    }

    // --- enter ---

    @Test
    void enter_createsExternalFileAndSentinel() throws IOException {
        MonitoringMode.enter(workspacePath);
        assertTrue(Files.exists(MonitoringMode.externalPath(workspacePath)));
        assertTrue(Files.exists(MonitoringMode.sentinelPath(workspacePath)));
    }

    @Test
    void enter_externalFileIsValidWorkspace() throws IOException {
        MonitoringMode.enter(workspacePath);
        var loaded = repo.load(MonitoringMode.externalPath(workspacePath));
        assertEquals(base.getRootNodeId(), loaded.getRootNodeId());
    }

    // --- reject ---

    @Test
    void reject_removesExternalAndSentinel() throws IOException {
        MonitoringMode.enter(workspacePath);
        MonitoringMode.reject(workspacePath);
        assertFalse(Files.exists(MonitoringMode.externalPath(workspacePath)));
        assertFalse(Files.exists(MonitoringMode.sentinelPath(workspacePath)));
    }

    @Test
    void reject_leavesWorkspaceUntouched() throws IOException {
        MonitoringMode.enter(workspacePath);
        MonitoringMode.reject(workspacePath);
        var loaded = repo.load(workspacePath);
        assertEquals(base.getRootNodeId(), loaded.getRootNodeId());
    }

    @Test
    void reject_isIdempotentWhenNoFilesExist() {
        assertDoesNotThrow(() -> MonitoringMode.reject(workspacePath));
    }

    // --- accept ---

    @Test
    void accept_replacesWorkspaceWithExternal() throws IOException {
        MonitoringMode.enter(workspacePath);
        addInboxItem(MonitoringMode.externalPath(workspacePath), "New item");
        MonitoringMode.accept(workspacePath);
        var loaded = repo.load(workspacePath);
        assertTrue(loaded.getInboxItems().stream().anyMatch(n -> n.getTitle().equals("New item")));
    }

    @Test
    void accept_removesExternalAndSentinel() throws IOException {
        MonitoringMode.enter(workspacePath);
        MonitoringMode.accept(workspacePath);
        assertFalse(Files.exists(MonitoringMode.externalPath(workspacePath)));
        assertFalse(Files.exists(MonitoringMode.sentinelPath(workspacePath)));
    }

    // --- exit: NoChanges ---

    @Test
    void exit_noChanges_whenExternalFileAbsent() {
        var result = MonitoringMode.exit(workspacePath, repo);
        assertInstanceOf(MonitoringMode.ExitResult.NoChanges.class, result);
    }

    @Test
    void exit_noChanges_whenExternalMatchesBase() throws IOException {
        MonitoringMode.enter(workspacePath);
        var result = MonitoringMode.exit(workspacePath, repo);
        assertInstanceOf(MonitoringMode.ExitResult.NoChanges.class, result);
    }

    // --- exit: HasChanges ---

    @Test
    void exit_hasChanges_inboxItemAdded() throws IOException {
        MonitoringMode.enter(workspacePath);
        addInboxItem(MonitoringMode.externalPath(workspacePath), "Capture");
        var result = MonitoringMode.exit(workspacePath, repo);
        assertInstanceOf(MonitoringMode.ExitResult.HasChanges.class, result);
        var summary = ((MonitoringMode.ExitResult.HasChanges) result).summary();
        assertEquals(1, summary.inboxAdded());
        assertEquals(0, summary.projectsCreated());
        assertEquals(0, summary.actionsAdded());
        assertEquals(0, summary.statusChanged());
        assertEquals(0, summary.deleted());
    }

    @Test
    void exit_hasChanges_projectCreated() throws IOException {
        MonitoringMode.enter(workspacePath);
        addProject(MonitoringMode.externalPath(workspacePath), "New project");
        var result = MonitoringMode.exit(workspacePath, repo);
        var summary = ((MonitoringMode.ExitResult.HasChanges) result).summary();
        assertEquals(1, summary.projectsCreated());
        assertEquals(0, summary.inboxAdded());
    }

    @Test
    void exit_hasChanges_actionAdded() throws IOException {
        MonitoringMode.enter(workspacePath);
        addAction(MonitoringMode.externalPath(workspacePath), "Do something");
        var result = MonitoringMode.exit(workspacePath, repo);
        var summary = ((MonitoringMode.ExitResult.HasChanges) result).summary();
        assertEquals(1, summary.actionsAdded());
        assertEquals(0, summary.inboxAdded());
    }

    @Test
    void exit_hasChanges_statusChanged() throws IOException {
        var action = new NamNode(UUID.randomUUID(), "Existing action");
        action.setStatus(NodeStatus.BACKLOG);
        base.getNodes().put(action.getId(), action);
        repo.save(workspacePath, base);

        MonitoringMode.enter(workspacePath);
        var ext = repo.load(MonitoringMode.externalPath(workspacePath));
        ext.getNode(action.getId()).orElseThrow().setStatus(NodeStatus.NEXT);
        repo.save(MonitoringMode.externalPath(workspacePath), ext);

        var result = MonitoringMode.exit(workspacePath, repo);
        var summary = ((MonitoringMode.ExitResult.HasChanges) result).summary();
        assertEquals(1, summary.statusChanged());
    }

    @Test
    void exit_hasChanges_nodeDeleted() throws IOException {
        var action = new NamNode(UUID.randomUUID(), "Will be deleted");
        action.setStatus(NodeStatus.BACKLOG);
        base.getNodes().put(action.getId(), action);
        repo.save(workspacePath, base);

        MonitoringMode.enter(workspacePath);
        var ext = repo.load(MonitoringMode.externalPath(workspacePath));
        ext.getNodes().remove(action.getId());
        repo.save(MonitoringMode.externalPath(workspacePath), ext);

        var result = MonitoringMode.exit(workspacePath, repo);
        var summary = ((MonitoringMode.ExitResult.HasChanges) result).summary();
        assertEquals(1, summary.deleted());
    }

    @Test
    void exit_hasChanges_multipleChangeTypes() throws IOException {
        MonitoringMode.enter(workspacePath);
        var ext = MonitoringMode.externalPath(workspacePath);
        addInboxItem(ext, "Item 1");
        addInboxItem(ext, "Item 2");
        addProject(ext, "Project A");
        var result = MonitoringMode.exit(workspacePath, repo);
        var summary = ((MonitoringMode.ExitResult.HasChanges) result).summary();
        assertEquals(2, summary.inboxAdded());
        assertEquals(1, summary.projectsCreated());
    }

    // --- exit: Unparseable ---

    @Test
    void exit_unparseable_whenExternalIsCorrupt() throws IOException {
        MonitoringMode.enter(workspacePath);
        Files.writeString(MonitoringMode.externalPath(workspacePath), "not json {{{");
        var result = MonitoringMode.exit(workspacePath, repo);
        assertInstanceOf(MonitoringMode.ExitResult.Unparseable.class, result);
    }

    // --- DiffSummary.describe ---

    @Test
    void describe_singleInboxItem() {
        var s = new MonitoringMode.DiffSummary(1, 0, 0, 0, 0, 0, 0);
        assertEquals("1 item added to Inbox", s.describe());
    }

    @Test
    void describe_pluralInboxItems() {
        var s = new MonitoringMode.DiffSummary(3, 0, 0, 0, 0, 0, 0);
        assertEquals("3 items added to Inbox", s.describe());
    }

    @Test
    void describe_mixedChanges() {
        var s = new MonitoringMode.DiffSummary(2, 1, 0, 3, 0, 0, 0);
        assertTrue(s.describe().contains("2 items added to Inbox"));
        assertTrue(s.describe().contains("1 project created"));
        assertTrue(s.describe().contains("3 status changes"));
    }

    @Test
    void describe_isEmpty_whenAllZero() {
        assertTrue(new MonitoringMode.DiffSummary(0, 0, 0, 0, 0, 0, 0).isEmpty());
    }

    @Test
    void describe_notEmpty_whenAnyNonZero() {
        assertFalse(new MonitoringMode.DiffSummary(0, 0, 1, 0, 0, 0, 0).isEmpty());
    }

    @Test
    void describe_resourceChange() {
        var s = new MonitoringMode.DiffSummary(0, 0, 0, 0, 0, 2, 0);
        assertTrue(s.describe().contains("2 resource changes"));
        assertFalse(s.isEmpty());
    }

    @Test
    void diff_detectsNodeMoved(@TempDir Path tmpDir) throws IOException {
        var wsPath    = tmpDir.resolve("workspace.json");
        var base      = NamWorkspace.createDefault();
        var projectA  = new NamNode(UUID.randomUUID(), "Project A");
        projectA.setProject(true);
        var projectB  = new NamNode(UUID.randomUUID(), "Project B");
        projectB.setProject(true);
        var action    = new NamNode(UUID.randomUUID(), "Some action");
        base.getNodes().put(projectA.getId(), projectA);
        base.getNodes().put(projectB.getId(), projectB);
        base.getNodes().put(action.getId(), action);
        base.getNode(base.getProjectsNodeId()).orElseThrow().getChildIds().add(projectA.getId());
        base.getNode(base.getProjectsNodeId()).orElseThrow().getChildIds().add(projectB.getId());
        projectA.getChildIds().add(action.getId());
        repo.save(wsPath, base);

        var ext = repo.load(wsPath);
        ext.getNode(projectA.getId()).orElseThrow().getChildIds().remove(action.getId());
        ext.getNode(projectB.getId()).orElseThrow().getChildIds().add(action.getId());

        var summary = MonitoringMode.diff(base, ext);
        assertEquals(1, summary.moved());
        assertFalse(summary.isEmpty());
        assertTrue(summary.describe().contains("1 node moved"));
    }

    @Test
    void diff_detectsResourceAddedToExistingNode(@TempDir Path tmpDir) throws IOException {
        var wsPath = tmpDir.resolve("workspace.json");
        var base   = NamWorkspace.createDefault();
        var node   = new NamNode(UUID.randomUUID(), "Buy milk");
        base.getNodes().put(node.getId(), node);
        base.getNode(base.getInboxNodeId()).orElseThrow().getChildIds().add(node.getId());
        repo.save(wsPath, base);

        var ext = repo.load(wsPath);
        ext.getNode(node.getId()).orElseThrow().getResources().add(new Resource(ResourceType.TEXT, "note", null));

        var summary = MonitoringMode.diff(base, ext);
        assertEquals(1, summary.resourcesChanged());
        assertFalse(summary.isEmpty());
    }

    // --- helpers ---

    private void addInboxItem(Path path, String title) throws IOException {
        var ws = repo.load(path);
        var node = new NamNode(UUID.randomUUID(), title);
        node.setStatus(NodeStatus.BACKLOG);
        ws.getNodes().put(node.getId(), node);
        ws.getNode(ws.getInboxNodeId()).orElseThrow().getChildIds().add(node.getId());
        repo.save(path, ws);
    }

    private void addProject(Path path, String title) throws IOException {
        var ws = repo.load(path);
        var node = new NamNode(UUID.randomUUID(), title);
        node.setProject(true);
        ws.getNodes().put(node.getId(), node);
        ws.getNode(ws.getProjectsNodeId()).orElseThrow().getChildIds().add(node.getId());
        repo.save(path, ws);
    }

    private void addAction(Path path, String title) throws IOException {
        var ws = repo.load(path);
        var node = new NamNode(UUID.randomUUID(), title);
        node.setStatus(NodeStatus.NEXT);
        ws.getNodes().put(node.getId(), node);
        ws.getNode(ws.getNextActionsNodeId()).orElseThrow().getChildIds().add(node.getId());
        repo.save(path, ws);
    }
}
