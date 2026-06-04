package namdesktop.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.persist.JsonWorkspaceRepository;
import namdesktop.service.MonitoringMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NamMcpServerTest {

    @TempDir Path dir;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Path workspacePath;
    private NamMcpServer server;
    private JsonWorkspaceRepository repo;

    @BeforeEach
    void setUp() throws IOException {
        workspacePath = dir.resolve("workspace.json");
        repo = new JsonWorkspaceRepository();
        repo.save(workspacePath, NamWorkspace.createDefault());
        MonitoringMode.enter(workspacePath);
        server = new NamMcpServer(workspacePath);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ObjectNode call(String tool, String json) throws IOException {
        var args = json.isBlank() ? MAPPER.createObjectNode() : (ObjectNode) MAPPER.readTree(json);
        return server.callTool(tool, args);
    }

    private String text(ObjectNode result) {
        return result.path("content").get(0).path("text").asText();
    }

    private String extractId(ObjectNode result) {
        var m = java.util.regex.Pattern.compile("([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})")
                .matcher(text(result));
        assertTrue(m.find(), "No UUID found in: " + text(result));
        return m.group(1);
    }

    private boolean isError(ObjectNode result) {
        return result.path("isError").asBoolean();
    }

    private NamWorkspace loadExternal() throws IOException {
        return repo.load(MonitoringMode.externalPath(workspacePath));
    }

    // -------------------------------------------------------------------------
    // Monitoring mode guard
    // -------------------------------------------------------------------------

    @Test
    void writeTool_rejectsWhenMonitoringModeOff() throws IOException {
        MonitoringMode.reject(workspacePath);
        var result = call("add_inbox_item", "{\"title\":\"test\"}");
        assertFalse(isError(result));
        assertTrue(text(result).contains("not active"));
    }

    // -------------------------------------------------------------------------
    // Direct mode
    // -------------------------------------------------------------------------

    @Test
    void directMode_writesDirectlyToWorkspaceJson() throws IOException {
        MonitoringMode.reject(workspacePath); // monitoring OFF
        var direct = new NamMcpServer(workspacePath, true);
        var args   = (ObjectNode) MAPPER.readTree("{\"title\":\"Direct item\"}");
        var result = direct.callTool("add_inbox_item", args);
        assertFalse(isError(result));

        var loaded = repo.load(workspacePath);
        assertTrue(loaded.getInboxItems().stream()
                .anyMatch(n -> "Direct item".equals(n.getTitle())));
    }

    @Test
    void directMode_monitoringStatus_reportsDirect() throws IOException {
        var direct = new NamMcpServer(workspacePath, true);
        var result = direct.callTool("get_monitoring_status", MAPPER.createObjectNode());
        assertTrue(text(result).contains("direct mode"));
    }

    @Test
    void directMode_doesNotRequireMonitoringMode() throws IOException {
        MonitoringMode.reject(workspacePath);
        var direct = new NamMcpServer(workspacePath, true);
        var args   = (ObjectNode) MAPPER.readTree("{\"title\":\"No monitoring needed\"}");
        var result = direct.callTool("add_next_action", args);
        assertFalse(isError(result));
    }

    @Test
    void directSentinelPath_isInWorkspaceDirectory() {
        var sentinel = NamMcpServer.directSentinelPath(workspacePath);
        assertEquals(workspacePath.getParent(), sentinel.getParent());
        assertEquals(".namdesktop-direct", sentinel.getFileName().toString());
    }

    // -------------------------------------------------------------------------
    // add_inbox_item
    // -------------------------------------------------------------------------

    @Test
    void addInboxItem_addsNodeToInbox() throws IOException {
        call("add_inbox_item", "{\"title\":\"Buy milk\"}");
        var ws = loadExternal();
        var inbox = ws.getInboxItems();
        assertTrue(inbox.stream().anyMatch(n -> n.getTitle().equals("Buy milk")));
    }

    @Test
    void addInboxItem_nodeHasBacklogStatus() throws IOException {
        call("add_inbox_item", "{\"title\":\"Buy milk\"}");
        var ws   = loadExternal();
        var node = ws.getInboxItems().stream().filter(n -> n.getTitle().equals("Buy milk")).findFirst().orElseThrow();
        assertEquals(NodeStatus.BACKLOG, node.getStatus());
    }

    @Test
    void addInboxItem_requiresTitle() throws IOException {
        var result = call("add_inbox_item", "{}");
        assertTrue(isError(result));
    }

    // -------------------------------------------------------------------------
    // add_next_action
    // -------------------------------------------------------------------------

    @Test
    void addNextAction_addsNodeWithNextStatus() throws IOException {
        call("add_next_action", "{\"title\":\"Call dentist\"}");
        var ws   = loadExternal();
        var node = ws.getNodes().values().stream()
                .filter(n -> n.getTitle().equals("Call dentist")).findFirst().orElseThrow();
        assertEquals(NodeStatus.NEXT, node.getStatus());
    }

    @Test
    void addNextAction_nodeIsChildOfNextActionsContainer() throws IOException {
        call("add_next_action", "{\"title\":\"Call dentist\"}");
        var ws     = loadExternal();
        var node   = ws.getNodes().values().stream()
                .filter(n -> n.getTitle().equals("Call dentist")).findFirst().orElseThrow();
        var parent = ws.getNode(ws.getNextActionsNodeId()).orElseThrow();
        assertTrue(parent.getChildIds().contains(node.getId()));
    }

    // -------------------------------------------------------------------------
    // create_project
    // -------------------------------------------------------------------------

    @Test
    void createProject_createsTopLevelProject() throws IOException {
        var result = call("create_project", "{\"title\":\"Travel\"}");
        assertFalse(isError(result));
        var ws      = loadExternal();
        var project = ws.getChildren(ws.getProjectsNodeId()).stream()
                .filter(n -> n.getTitle().equals("Travel")).findFirst();
        assertTrue(project.isPresent());
        assertTrue(project.get().isProject());
    }

    @Test
    void createProject_createsSubProject() throws IOException {
        var r      = call("create_project", "{\"title\":\"Travel\"}");
        var id     = MAPPER.readTree(text(r).replaceAll(".*with id ([a-f0-9-]+).*", "\"$1\"")).asText();
        var result = call("create_project", "{\"title\":\"Packing\",\"parent_id\":\"" + id + "\"}");
        assertFalse(isError(result));
        var ws   = loadExternal();
        var parent = ws.getNodes().values().stream().filter(n -> n.getTitle().equals("Travel")).findFirst().orElseThrow();
        assertTrue(parent.getChildIds().stream().anyMatch(cid ->
                ws.getNode(cid).map(n -> n.getTitle().equals("Packing")).orElse(false)));
    }

    @Test
    void createProject_rejectsUnknownParentId() throws IOException {
        var result = call("create_project",
                "{\"title\":\"X\",\"parent_id\":\"00000000-0000-0000-0000-000000000000\"}");
        assertTrue(isError(result));
    }

    // -------------------------------------------------------------------------
    // add_action
    // -------------------------------------------------------------------------

    @Test
    void addAction_addsActionToProject() throws IOException {
        var pr = call("create_project", "{\"title\":\"Work\"}");
        var id = extractId(pr);
        call("add_action", "{\"title\":\"Write report\",\"project_id\":\"" + id + "\"}");
        var ws     = loadExternal();
        var parent = ws.getNodes().values().stream().filter(n -> n.getTitle().equals("Work")).findFirst().orElseThrow();
        assertTrue(parent.getChildIds().stream().anyMatch(cid ->
                ws.getNode(cid).map(n -> n.getTitle().equals("Write report")).orElse(false)));
    }

    @Test
    void addAction_defaultStatusIsBacklog() throws IOException {
        var pr = call("create_project", "{\"title\":\"Work\"}");
        var id = extractId(pr);
        call("add_action", "{\"title\":\"Write report\",\"project_id\":\"" + id + "\"}");
        var ws   = loadExternal();
        var node = ws.getNodes().values().stream().filter(n -> n.getTitle().equals("Write report")).findFirst().orElseThrow();
        assertEquals(NodeStatus.BACKLOG, node.getStatus());
    }

    @Test
    void addAction_rejectsUnknownProjectId() throws IOException {
        var result = call("add_action",
                "{\"title\":\"X\",\"project_id\":\"00000000-0000-0000-0000-000000000000\"}");
        assertTrue(isError(result));
    }

    // -------------------------------------------------------------------------
    // mark_next / mark_done / mark_backlog
    // -------------------------------------------------------------------------

    @Test
    void markDone_changesNodeStatus() throws IOException {
        call("add_next_action", "{\"title\":\"Call dentist\"}");
        var ws  = loadExternal();
        var id  = ws.getNodes().values().stream()
                .filter(n -> n.getTitle().equals("Call dentist")).findFirst().orElseThrow().getId();
        call("mark_done", "{\"node_id\":\"" + id + "\"}");
        assertEquals(NodeStatus.DONE, loadExternal().getNode(id).orElseThrow().getStatus());
    }

    @Test
    void markBacklog_changesNodeStatus() throws IOException {
        call("add_next_action", "{\"title\":\"Call dentist\"}");
        var ws  = loadExternal();
        var id  = ws.getNodes().values().stream()
                .filter(n -> n.getTitle().equals("Call dentist")).findFirst().orElseThrow().getId();
        call("mark_backlog", "{\"node_id\":\"" + id + "\"}");
        assertEquals(NodeStatus.BACKLOG, loadExternal().getNode(id).orElseThrow().getStatus());
    }

    @Test
    void markNext_rejectsUnknownNodeId() throws IOException {
        var result = call("mark_next", "{\"node_id\":\"00000000-0000-0000-0000-000000000000\"}");
        assertTrue(isError(result));
    }

    // -------------------------------------------------------------------------
    // delete_node
    // -------------------------------------------------------------------------

    @Test
    void deleteNode_removesNodeFromWorkspace() throws IOException {
        call("add_inbox_item", "{\"title\":\"Temp\"}");
        var ws  = loadExternal();
        var id  = ws.getInboxItems().stream().filter(n -> n.getTitle().equals("Temp")).findFirst().orElseThrow().getId();
        call("delete_node", "{\"node_id\":\"" + id + "\"}");
        assertTrue(loadExternal().getNode(id).isEmpty());
    }

    @Test
    void deleteNode_removesIdFromParentChildList() throws IOException {
        call("add_inbox_item", "{\"title\":\"Temp\"}");
        var ws  = loadExternal();
        var id  = ws.getInboxItems().stream().filter(n -> n.getTitle().equals("Temp")).findFirst().orElseThrow().getId();
        call("delete_node", "{\"node_id\":\"" + id + "\"}");
        var inbox = loadExternal().getNode(loadExternal().getInboxNodeId()).orElseThrow();
        assertFalse(inbox.getChildIds().contains(id));
    }

    @Test
    void deleteNode_rejectsNodeWithChildren() throws IOException {
        var pr = call("create_project", "{\"title\":\"Work\"}");
        var id = extractId(pr);
        call("add_action", "{\"title\":\"Write report\",\"project_id\":\"" + id + "\"}");
        var result = call("delete_node", "{\"node_id\":\"" + id + "\"}");
        assertTrue(isError(result));
    }

    // -------------------------------------------------------------------------
    // list_inbox
    // -------------------------------------------------------------------------

    @Test
    void listInbox_returnsInboxItems() throws IOException {
        call("add_inbox_item", "{\"title\":\"Buy milk\"}");
        var result = text(call("list_inbox", ""));
        assertTrue(result.contains("Buy milk"));
    }

    @Test
    void listInbox_includesBlockedByArray() throws IOException {
        call("add_inbox_item", "{\"title\":\"Buy milk\"}");
        var result = text(call("list_inbox", ""));
        assertTrue(result.contains("blocked_by"));
    }

    // -------------------------------------------------------------------------
    // list_next_actions
    // -------------------------------------------------------------------------

    @Test
    void listNextActions_returnsNextNodes() throws IOException {
        call("add_next_action", "{\"title\":\"Call dentist\"}");
        var result = text(call("list_next_actions", ""));
        assertTrue(result.contains("Call dentist"));
    }

    @Test
    void listNextActions_doesNotReturnBacklogNodes() throws IOException {
        call("add_inbox_item", "{\"title\":\"Inbox item\"}");
        var result = text(call("list_next_actions", ""));
        assertFalse(result.contains("Inbox item"));
    }

    // -------------------------------------------------------------------------
    // list_done
    // -------------------------------------------------------------------------

    @Test
    void listDone_returnsDoneNodes() throws IOException {
        call("add_next_action", "{\"title\":\"Call dentist\"}");
        var ws = loadExternal();
        var id = ws.getNodes().values().stream()
                .filter(n -> n.getTitle().equals("Call dentist")).findFirst().orElseThrow().getId();
        call("mark_done", "{\"node_id\":\"" + id + "\"}");
        assertTrue(text(call("list_done", "")).contains("Call dentist"));
    }

    // -------------------------------------------------------------------------
    // list_projects
    // -------------------------------------------------------------------------

    @Test
    void listProjects_returnsTopLevelProjects() throws IOException {
        call("create_project", "{\"title\":\"Travel\"}");
        assertTrue(text(call("list_projects", "")).contains("Travel"));
    }

    // -------------------------------------------------------------------------
    // list_saved_views
    // -------------------------------------------------------------------------

    @Test
    void listSavedViews_returnsEmptyArrayForDefaultWorkspace() throws IOException {
        var result = text(call("list_saved_views", ""));
        assertEquals("[ ]", result.strip());
    }

    // -------------------------------------------------------------------------
    // find_node
    // -------------------------------------------------------------------------

    @Test
    void findNode_matchesBySubstring() throws IOException {
        call("add_inbox_item", "{\"title\":\"Buy birthday cake\"}");
        assertTrue(text(call("find_node", "{\"title\":\"birthday\"}")).contains("Buy birthday cake"));
    }

    @Test
    void findNode_isCaseInsensitive() throws IOException {
        call("add_inbox_item", "{\"title\":\"Buy Birthday Cake\"}");
        assertTrue(text(call("find_node", "{\"title\":\"birthday\"}")).contains("Buy Birthday Cake"));
    }

    @Test
    void findNode_returnsEmptyArrayWhenNoMatch() throws IOException {
        var result = text(call("find_node", "{\"title\":\"zzznomatch\"}"));
        assertEquals("[ ]", result.strip());
    }

    // -------------------------------------------------------------------------
    // list_project_children
    // -------------------------------------------------------------------------

    @Test
    void listProjectChildren_returnsChildren() throws IOException {
        var pr = call("create_project", "{\"title\":\"Work\"}");
        var id = extractId(pr);
        call("add_action", "{\"title\":\"Write report\",\"project_id\":\"" + id + "\"}");
        assertTrue(text(call("list_project_children", "{\"project_id\":\"" + id + "\"}")).contains("Write report"));
    }

    @Test
    void listProjectChildren_returnsEmptyForProjectWithNoChildren() throws IOException {
        var pr = call("create_project", "{\"title\":\"Empty project\"}");
        var id = extractId(pr);
        var result = text(call("list_project_children", "{\"project_id\":\"" + id + "\"}"));
        assertEquals("[ ]", result.strip());
    }

    // -------------------------------------------------------------------------
    // get_monitoring_status
    // -------------------------------------------------------------------------

    @Test
    void getMonitoringStatus_reportsActiveWhenOn() throws IOException {
        assertTrue(text(call("get_monitoring_status", "")).contains("ACTIVE"));
    }

    @Test
    void getMonitoringStatus_reportsOffWhenInactive() throws IOException {
        MonitoringMode.reject(workspacePath);
        assertTrue(text(call("get_monitoring_status", "")).contains("OFF"));
    }
}
