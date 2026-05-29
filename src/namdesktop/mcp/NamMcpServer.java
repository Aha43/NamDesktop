package namdesktop.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.persist.JsonWorkspaceRepository;
import namdesktop.service.MonitoringMode;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * MCP stdio server — run from the same JAR as NamDesktop.
 *
 * Usage:
 *   java -cp namdesktop.jar namdesktop.mcp.NamMcpServer --workspace /path/to/workspace.json
 *
 * Add to ~/.claude/claude_desktop_config.json:
 *   { "mcpServers": { "namdesktop": {
 *       "command": "java",
 *       "args": ["-cp", "/path/to/NamDesktop.jar:lib/*",
 *                "namdesktop.mcp.NamMcpServer",
 *                "--workspace", "/Users/<you>/.namdesktop/workspace.json"]
 *   }}}
 */
public final class NamMcpServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String NOT_MONITORING =
            "Monitoring mode is not active. NamDesktop is not watching for external changes. " +
            "Please enable monitoring mode first: press Cmd+Shift+M in NamDesktop or click the " +
            "antenna button in the toolbar. Then retry this operation.";

    private final Path workspacePath;
    private final JsonWorkspaceRepository repo = new JsonWorkspaceRepository();

    public static void main(String[] args) throws IOException {
        Path workspace = null;
        for (int i = 0; i < args.length - 1; i++) {
            if ("--workspace".equals(args[i])) workspace = Path.of(args[i + 1]);
        }
        if (workspace == null) {
            System.err.println("[namdesktop-mcp] --workspace <path> is required");
            System.exit(1);
        }
        new NamMcpServer(workspace).run();
    }

    NamMcpServer(Path workspacePath) {
        this.workspacePath = workspacePath;
    }

    // -------------------------------------------------------------------------
    // Stdio loop
    // -------------------------------------------------------------------------

    private void run() throws IOException {
        var in  = new BufferedReader(new InputStreamReader(System.in));
        var out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out)), false);
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) continue;
            try {
                var msg      = MAPPER.readTree(line);
                var response = handle(msg);
                if (response != null) {
                    out.println(MAPPER.writeValueAsString(response));
                    out.flush();
                }
            } catch (Exception e) {
                System.err.println("[namdesktop-mcp] error: " + e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // JSON-RPC routing
    // -------------------------------------------------------------------------

    private ObjectNode handle(JsonNode msg) throws IOException {
        var method = msg.path("method").asText("");
        var id     = msg.path("id");   // absent for notifications
        return switch (method) {
            case "initialize"               -> respond(id, initializeResult());
            case "notifications/initialized"-> null;
            case "tools/list"               -> respond(id, toolsList());
            case "tools/call"               -> respond(id, callTool(
                    msg.path("params").path("name").asText(""),
                    msg.path("params").path("arguments")));
            default                         -> null;
        };
    }

    private ObjectNode respond(JsonNode id, ObjectNode result) {
        var r = MAPPER.createObjectNode();
        r.put("jsonrpc", "2.0");
        r.set("id", id);
        r.set("result", result);
        return r;
    }

    // -------------------------------------------------------------------------
    // initialize
    // -------------------------------------------------------------------------

    private ObjectNode initializeResult() {
        var result = MAPPER.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        var info = result.putObject("serverInfo");
        info.put("name", "namdesktop");
        info.put("version", "1.0");
        result.putObject("capabilities").putObject("tools");
        return result;
    }

    // -------------------------------------------------------------------------
    // tools/list
    // -------------------------------------------------------------------------

    private ObjectNode toolsList() {
        var result = MAPPER.createObjectNode();
        var tools  = result.putArray("tools");
        tools.add(tool("get_workspace_context",
                "Get a compact summary of the workspace: projects and tags in use.",
                MAPPER.createObjectNode()));
        tools.add(tool("list_inbox",
                "List all items currently in the Inbox.",
                MAPPER.createObjectNode()));
        tools.add(tool("list_projects",
                "List all top-level projects.",
                MAPPER.createObjectNode()));
        tools.add(tool("list_next_actions",
                "List all actions with status NEXT across the whole workspace.",
                MAPPER.createObjectNode()));
        tools.add(tool("list_done",
                "List all actions with status DONE across the whole workspace.",
                MAPPER.createObjectNode()));
        tools.add(tool("list_saved_views",
                "List the saved views (user-defined tag filters) defined in the workspace.",
                MAPPER.createObjectNode()));
        tools.add(tool("list_project_children",
                "List the direct children of a project node. Use to verify structure before writing.",
                schema(Map.of("project_id", prop("string", "UUID of the project")), List.of("project_id"))));
        tools.add(tool("find_node",
                "Find nodes by title (case-insensitive substring match). Returns id, title, status, project flag, tags.",
                schema(Map.of("title", prop("string", "Substring to search for")), List.of("title"))));
        tools.add(tool("get_monitoring_status",
                "Check whether NamDesktop is in monitoring mode (ready to receive writes).",
                MAPPER.createObjectNode()));
        tools.add(tool("create_project",
                "Create a new project. Optionally nest it under a parent project via parent_id. Requires monitoring mode.",
                schema(Map.of(
                        "title",       prop("string", "Title of the project"),
                        "description", prop("string", "Optional description"),
                        "tags",        tagsArrayProp(),
                        "parent_id",   prop("string", "Optional UUID of parent project; omit for top-level")
                ), List.of("title"))));
        tools.add(tool("add_action",
                "Add an action as a child of a specific project. Requires monitoring mode.",
                schema(Map.of(
                        "title",       prop("string", "Title of the action"),
                        "project_id",  prop("string", "UUID of the parent project"),
                        "description", prop("string", "Optional description"),
                        "tags",        tagsArrayProp(),
                        "status",      prop("string", "BACKLOG (default), NEXT, or DONE"),
                        "blocked_by",  uuidArrayProp("Optional list of node UUIDs this action is blocked by")
                ), List.of("title", "project_id"))));
        tools.add(tool("add_inbox_item",
                "Add an item to the Inbox. Requires monitoring mode to be active.",
                schema(Map.of(
                        "title",       prop("string", "Title of the inbox item"),
                        "description", prop("string", "Optional description"),
                        "tags",        tagsArrayProp(),
                        "blocked_by",  uuidArrayProp("Optional list of node UUIDs this item is blocked by")
                ), List.of("title"))));
        tools.add(tool("add_next_action",
                "Add a new action directly to the Next Actions list with status NEXT. Requires monitoring mode.",
                schema(Map.of(
                        "title",       prop("string", "Title of the action"),
                        "description", prop("string", "Optional description"),
                        "tags",        tagsArrayProp(),
                        "blocked_by",  uuidArrayProp("Optional list of node UUIDs this action is blocked by")
                ), List.of("title"))));
        tools.add(tool("delete_node",
                "Delete a node by id. Node must have no children. Requires monitoring mode.",
                schema(Map.of("node_id", prop("string", "UUID of the node to delete")), List.of("node_id"))));
        tools.add(tool("mark_next",
                "Set a node's status to NEXT. Requires monitoring mode.",
                schema(Map.of("node_id", prop("string", "UUID of the node")), List.of("node_id"))));
        tools.add(tool("mark_done",
                "Set a node's status to DONE. Requires monitoring mode.",
                schema(Map.of("node_id", prop("string", "UUID of the node")), List.of("node_id"))));
        tools.add(tool("mark_backlog",
                "Set a node's status to BACKLOG. Requires monitoring mode.",
                schema(Map.of("node_id", prop("string", "UUID of the node")), List.of("node_id"))));
        return result;
    }

    private ObjectNode tool(String name, String description, ObjectNode inputSchema) {
        var t = MAPPER.createObjectNode();
        t.put("name", name);
        t.put("description", description);
        t.set("inputSchema", inputSchema);
        return t;
    }

    private ObjectNode schema(Map<String, ObjectNode> props, List<String> required) {
        var s = MAPPER.createObjectNode();
        s.put("type", "object");
        var p = s.putObject("properties");
        props.forEach(p::set);
        var req = s.putArray("required");
        required.forEach(req::add);
        return s;
    }

    private ObjectNode prop(String type, String description) {
        var p = MAPPER.createObjectNode();
        p.put("type", type);
        p.put("description", description);
        return p;
    }

    private ObjectNode tagsArrayProp() {
        var p = MAPPER.createObjectNode();
        p.put("type", "array");
        p.put("description", "Optional list of @-prefixed tags, e.g. [\"@computer\", \"@home\"]");
        p.putObject("items").put("type", "string");
        return p;
    }

    private ObjectNode uuidArrayProp(String description) {
        var p = MAPPER.createObjectNode();
        p.put("type", "array");
        p.put("description", description);
        p.putObject("items").put("type", "string");
        return p;
    }

    // -------------------------------------------------------------------------
    // tools/call dispatch
    // -------------------------------------------------------------------------

    ObjectNode callTool(String name, JsonNode args) throws IOException {
        return switch (name) {
            case "get_workspace_context" -> toolGetContext();
            case "list_inbox"            -> toolListInbox();
            case "list_next_actions"     -> toolListNextActions();
            case "list_done"             -> toolListDone();
            case "list_projects"         -> toolListProjects();
            case "list_saved_views"      -> toolListSavedViews();
            case "list_project_children" -> toolListProjectChildren(args);
            case "find_node"             -> toolFindNode(args);
            case "get_monitoring_status" -> toolMonitoringStatus();
            case "add_inbox_item"        -> toolAddInboxItem(args);
            case "add_next_action"       -> toolAddNextAction(args);
            case "create_project"        -> toolCreateProject(args);
            case "add_action"            -> toolAddAction(args);
            case "delete_node"           -> toolDeleteNode(args);
            case "mark_next"             -> toolSetStatus(args, NodeStatus.NEXT);
            case "mark_done"             -> toolSetStatus(args, NodeStatus.DONE);
            case "mark_backlog"          -> toolSetStatus(args, NodeStatus.BACKLOG);
            default                      -> textResult("Unknown tool: " + name, true);
        };
    }

    // -------------------------------------------------------------------------
    // Read tools
    // -------------------------------------------------------------------------

    private Path readPath() {
        return MonitoringMode.isActive(workspacePath)
                ? MonitoringMode.externalPath(workspacePath)
                : workspacePath;
    }

    private ObjectNode toolGetContext() throws IOException {
        var ws = repo.load(readPath());
        var sb = new StringBuilder();
        sb.append("# Workspace context\n\n");
        sb.append("## Projects\n");
        for (var n : ws.getChildren(ws.getProjectsNodeId())) {
            var childCount = ws.getChildren(n.getId()).size();
            sb.append("- ").append(n.getTitle());
            if (!n.getTags().isEmpty()) sb.append("  tags: ").append(String.join(", ", n.getTags()));
            if (childCount > 0) sb.append("  (").append(childCount).append(" actions)");
            sb.append("\n");
        }
        var tags = ws.allTags();
        if (!tags.isEmpty()) {
            sb.append("\n## Tags in use\n");
            tags.forEach(t -> sb.append("- ").append(t).append("\n"));
        }
        var inboxCount = ws.getInboxItems().size();
        sb.append("\n## Inbox\n").append(inboxCount).append(" item(s) pending.\n");
        return textResult(sb.toString(), false);
    }

    private ObjectNode toolListInbox() throws IOException {
        var ws    = repo.load(readPath());
        var items = MAPPER.createArrayNode();
        for (var n : ws.getInboxItems()) {
            var o = items.addObject();
            o.put("id",     n.getId().toString());
            o.put("title",  n.getTitle());
            o.put("status", n.getStatus().name());
            if (n.getDescription() != null && !n.getDescription().isBlank())
                o.put("description", n.getDescription());
            var tags = o.putArray("tags");
            n.getTags().forEach(tags::add);
            var blocked = o.putArray("blocked_by");
            n.getBlockedBy().forEach(id -> blocked.add(id.toString()));
        }
        return textResult(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(items), false);
    }

    private ObjectNode toolListProjects() throws IOException {
        var ws       = repo.load(readPath());
        var projects = MAPPER.createArrayNode();
        for (var n : ws.getChildren(ws.getProjectsNodeId())) {
            var o = projects.addObject();
            o.put("id",          n.getId().toString());
            o.put("title",       n.getTitle());
            o.put("child_count", ws.getChildren(n.getId()).size());
            var tags = o.putArray("tags");
            n.getTags().forEach(tags::add);
        }
        return textResult(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(projects), false);
    }

    private ObjectNode toolListNextActions() throws IOException {
        var ws    = repo.load(readPath());
        var items = MAPPER.createArrayNode();
        ws.getNodes().values().stream()
                .filter(n -> n.getStatus() == NodeStatus.NEXT)
                .forEach(n -> {
                    var o = items.addObject();
                    o.put("id",      n.getId().toString());
                    o.put("title",   n.getTitle());
                    o.put("status",  n.getStatus().name());
                    o.put("project", n.isProject());
                    if (n.getDescription() != null && !n.getDescription().isBlank())
                        o.put("description", n.getDescription());
                    var tags = o.putArray("tags");
                    n.getTags().forEach(tags::add);
                    var blocked = o.putArray("blocked_by");
                    n.getBlockedBy().forEach(id -> blocked.add(id.toString()));
                });
        return textResult(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(items), false);
    }

    private ObjectNode toolListDone() throws IOException {
        var ws    = repo.load(readPath());
        var items = MAPPER.createArrayNode();
        ws.getNodes().values().stream()
                .filter(n -> n.getStatus() == NodeStatus.DONE)
                .forEach(n -> {
                    var o = items.addObject();
                    o.put("id",    n.getId().toString());
                    o.put("title", n.getTitle());
                    if (n.getDescription() != null && !n.getDescription().isBlank())
                        o.put("description", n.getDescription());
                    var tags = o.putArray("tags");
                    n.getTags().forEach(tags::add);
                });
        return textResult(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(items), false);
    }

    private ObjectNode toolListSavedViews() throws IOException {
        var ws    = repo.load(readPath());
        var views = MAPPER.createArrayNode();
        for (var v : ws.getSavedViews()) {
            var o = views.addObject();
            o.put("name",      v.name());
            o.put("next_only", v.nextOnly());
            var tags = o.putArray("tags");
            v.tags().forEach(tags::add);
        }
        return textResult(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(views), false);
    }

    private ObjectNode toolListProjectChildren(JsonNode args) throws IOException {
        var idStr = args.path("project_id").asText("").strip();
        if (idStr.isEmpty()) return textResult("Error: project_id is required.", true);
        UUID id;
        try { id = UUID.fromString(idStr); }
        catch (IllegalArgumentException e) { return textResult("Error: invalid project_id UUID.", true); }

        var ws   = repo.load(readPath());
        var node = ws.getNode(id).orElse(null);
        if (node == null) return textResult("Error: no node found with id " + idStr, true);

        var children = MAPPER.createArrayNode();
        for (var childId : node.getChildIds()) {
            ws.getNode(childId).ifPresent(child -> {
                var o = children.addObject();
                o.put("id",      child.getId().toString());
                o.put("title",   child.getTitle());
                o.put("status",  child.getStatus().name());
                o.put("project", child.isProject());
                var tags = o.putArray("tags");
                child.getTags().forEach(tags::add);
            });
        }
        return textResult(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(children), false);
    }

    private ObjectNode toolFindNode(JsonNode args) throws IOException {
        var query = args.path("title").asText("").strip().toLowerCase();
        if (query.isEmpty()) return textResult("Error: title is required.", true);
        var ws      = repo.load(readPath());
        var matches = MAPPER.createArrayNode();
        ws.getNodes().values().stream()
                .filter(n -> n.getTitle().toLowerCase().contains(query))
                .forEach(n -> {
                    var o = matches.addObject();
                    o.put("id",      n.getId().toString());
                    o.put("title",   n.getTitle());
                    o.put("status",  n.getStatus().name());
                    o.put("project", n.isProject());
                    var tags = o.putArray("tags");
                    n.getTags().forEach(tags::add);
                });
        return textResult(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(matches), false);
    }

    private ObjectNode toolMonitoringStatus() {
        var active = MonitoringMode.isActive(workspacePath);
        return textResult(active
                ? "Monitoring mode is ACTIVE. NamDesktop is watching for changes to workspace.external.json."
                : "Monitoring mode is OFF. Enable it in NamDesktop with Cmd+Shift+M before writing.", false);
    }

    // -------------------------------------------------------------------------
    // Write tools
    // -------------------------------------------------------------------------

    private ObjectNode toolAddInboxItem(JsonNode args) throws IOException {
        if (!MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
        var title = args.path("title").asText("").strip();
        if (title.isEmpty()) return textResult("Error: title is required.", true);

        atomicWrite(ws -> {
            var node = new NamNode(java.util.UUID.randomUUID(), title);
            node.setStatus(NodeStatus.BACKLOG);
            if (args.hasNonNull("description"))
                node.setDescription(args.path("description").asText());
            if (args.hasNonNull("tags")) {
                var tagList = new ArrayList<String>();
                args.path("tags").forEach(t -> tagList.add(t.asText()));
                node.setTags(tagList);
            }
            if (args.hasNonNull("blocked_by"))
                node.setBlockedBy(parseUuids(args.path("blocked_by"), ws));
            ws.getNodes().put(node.getId(), node);
            ws.getNode(ws.getInboxNodeId()).ifPresent(inbox -> inbox.getChildIds().add(node.getId()));
        });
        return textResult("Added \"" + title + "\" to Inbox.", false);
    }

    private ObjectNode toolAddNextAction(JsonNode args) throws IOException {
        if (!MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
        var title = args.path("title").asText("").strip();
        if (title.isEmpty()) return textResult("Error: title is required.", true);

        atomicWrite(ws -> {
            var node = new NamNode(java.util.UUID.randomUUID(), title);
            node.setStatus(NodeStatus.NEXT);
            if (args.hasNonNull("description"))
                node.setDescription(args.path("description").asText());
            if (args.hasNonNull("tags")) {
                var tagList = new ArrayList<String>();
                args.path("tags").forEach(t -> tagList.add(t.asText()));
                node.setTags(tagList);
            }
            if (args.hasNonNull("blocked_by"))
                node.setBlockedBy(parseUuids(args.path("blocked_by"), ws));
            ws.getNodes().put(node.getId(), node);
            ws.getNode(ws.getNextActionsNodeId()).ifPresent(next -> next.getChildIds().add(node.getId()));
        });
        return textResult("Added \"" + title + "\" to Next Actions.", false);
    }

    private ObjectNode toolCreateProject(JsonNode args) throws IOException {
        if (!MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
        var title = args.path("title").asText("").strip();
        if (title.isEmpty()) return textResult("Error: title is required.", true);

        var parentIdStr = args.path("parent_id").asText("").strip();
        var newId = UUID.randomUUID();

        var result = atomicWriteWithResult(ws -> {
            UUID parentId;
            if (parentIdStr.isEmpty()) {
                parentId = ws.getProjectsNodeId();
            } else {
                try { parentId = UUID.fromString(parentIdStr); }
                catch (IllegalArgumentException e) { return "Error: invalid parent_id UUID."; }
                if (ws.getNode(parentId).isEmpty()) return "Error: no node found with parent_id " + parentIdStr;
            }
            var node = new NamNode(newId, title);
            node.setProject(true);
            node.setStatus(NodeStatus.BACKLOG);
            if (args.hasNonNull("description")) node.setDescription(args.path("description").asText());
            if (args.hasNonNull("tags")) {
                var tagList = new ArrayList<String>();
                args.path("tags").forEach(t -> tagList.add(t.asText()));
                node.setTags(tagList);
            }
            ws.getNodes().put(newId, node);
            ws.getNode(parentId).ifPresent(p -> p.getChildIds().add(newId));
            return null;
        });
        if (result != null) return textResult(result, true);
        return textResult("Created project \"" + title + "\" with id " + newId + ".", false);
    }

    private ObjectNode toolAddAction(JsonNode args) throws IOException {
        if (!MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
        var title = args.path("title").asText("").strip();
        if (title.isEmpty()) return textResult("Error: title is required.", true);
        var projectIdStr = args.path("project_id").asText("").strip();
        if (projectIdStr.isEmpty()) return textResult("Error: project_id is required.", true);

        UUID projectId;
        try { projectId = UUID.fromString(projectIdStr); }
        catch (IllegalArgumentException e) { return textResult("Error: invalid project_id UUID.", true); }

        var ws = repo.load(MonitoringMode.externalPath(workspacePath));
        if (ws.getNode(projectId).isEmpty()) return textResult("Error: no node found with project_id " + projectIdStr, true);

        var statusStr = args.path("status").asText("BACKLOG").strip().toUpperCase();
        NodeStatus status;
        try { status = NodeStatus.valueOf(statusStr); }
        catch (IllegalArgumentException e) { return textResult("Error: invalid status \"" + statusStr + "\". Use BACKLOG, NEXT, or DONE.", true); }

        var newId = UUID.randomUUID();
        final UUID finalProjectId = projectId;
        final NodeStatus finalStatus = status;
        atomicWrite(w -> {
            var node = new NamNode(newId, title);
            node.setProject(false);
            node.setStatus(finalStatus);
            if (args.hasNonNull("description")) node.setDescription(args.path("description").asText());
            if (args.hasNonNull("tags")) {
                var tagList = new ArrayList<String>();
                args.path("tags").forEach(t -> tagList.add(t.asText()));
                node.setTags(tagList);
            }
            if (args.hasNonNull("blocked_by")) node.setBlockedBy(parseUuids(args.path("blocked_by"), w));
            w.getNodes().put(newId, node);
            w.getNode(finalProjectId).ifPresent(p -> p.getChildIds().add(newId));
        });
        return textResult("Added action \"" + title + "\" with id " + newId + " to project.", false);
    }

    private ObjectNode toolDeleteNode(JsonNode args) throws IOException {
        if (!MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
        var idStr = args.path("node_id").asText("").strip();
        if (idStr.isEmpty()) return textResult("Error: node_id is required.", true);

        UUID id;
        try { id = UUID.fromString(idStr); }
        catch (IllegalArgumentException e) { return textResult("Error: invalid node_id UUID.", true); }

        var ws   = repo.load(MonitoringMode.externalPath(workspacePath));
        var node = ws.getNode(id).orElse(null);
        if (node == null) return textResult("Error: no node found with id " + idStr, true);
        if (!node.getChildIds().isEmpty())
            return textResult("Error: \"" + node.getTitle() + "\" has children — clear them first or use the app.", true);

        var title = node.getTitle();
        final UUID finalId = id;
        atomicWrite(w -> {
            w.getNodes().remove(finalId);
            w.getNodes().values().forEach(n -> n.getChildIds().remove(finalId));
        });
        return textResult("Deleted \"" + title + "\".", false);
    }

    private ObjectNode toolSetStatus(JsonNode args, NodeStatus status) throws IOException {
        if (!MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
        var idStr = args.path("node_id").asText("").strip();
        if (idStr.isEmpty()) return textResult("Error: node_id is required.", true);

        java.util.UUID id;
        try { id = java.util.UUID.fromString(idStr); }
        catch (IllegalArgumentException e) { return textResult("Error: invalid node_id UUID.", true); }

        final java.util.UUID finalId = id;
        var ws = repo.load(MonitoringMode.externalPath(workspacePath));
        var node = ws.getNode(finalId).orElse(null);
        if (node == null) return textResult("Error: no node found with id " + idStr, true);
        var oldStatus = node.getStatus();

        atomicWrite(w -> w.getNode(finalId).ifPresent(n -> n.setStatus(status)));
        return textResult("Node \"" + node.getTitle() + "\" status changed from "
                + oldStatus.name() + " to " + status.name() + ".", false);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<UUID> parseUuids(JsonNode array, NamWorkspace ws) {
        var result = new ArrayList<UUID>();
        array.forEach(el -> {
            try {
                var id = UUID.fromString(el.asText());
                if (ws.getNode(id).isPresent()) result.add(id);
            } catch (IllegalArgumentException ignored) {}
        });
        return result;
    }

    // -------------------------------------------------------------------------
    // Atomic write helper
    // -------------------------------------------------------------------------

    private String atomicWriteWithResult(java.util.function.Function<NamWorkspace, String> mutate) throws IOException {
        var extPath = MonitoringMode.externalPath(workspacePath);
        var tmpPath = workspacePath.resolveSibling("workspace.external.tmp");
        var ws      = repo.load(extPath);
        var error   = mutate.apply(ws);
        if (error != null) return error;
        repo.save(tmpPath, ws);
        try {
            Files.move(tmpPath, extPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmpPath, extPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return null;
    }

    private void atomicWrite(java.util.function.Consumer<NamWorkspace> mutate) throws IOException {
        var extPath = MonitoringMode.externalPath(workspacePath);
        var tmpPath = workspacePath.resolveSibling("workspace.external.tmp");
        var ws      = repo.load(extPath);
        mutate.accept(ws);
        repo.save(tmpPath, ws);
        try {
            Files.move(tmpPath, extPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmpPath, extPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // -------------------------------------------------------------------------
    // Response helpers
    // -------------------------------------------------------------------------

    private ObjectNode textResult(String text, boolean isError) {
        var result  = MAPPER.createObjectNode();
        var content = result.putArray("content");
        var item    = content.addObject();
        item.put("type", "text");
        item.put("text", text);
        result.put("isError", isError);
        return result;
    }
}
