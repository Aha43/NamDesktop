package namdesktop.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.model.Resource;
import namdesktop.model.ResourceType;
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

    private static final String DIRECT_SENTINEL = ".namdesktop-direct";

    private final Path    workspacePath;
    private final boolean directMode;
    private final JsonWorkspaceRepository repo = new JsonWorkspaceRepository();

    public static void main(String[] args) throws IOException {
        Path workspace = null;
        boolean direct = false;
        for (int i = 0; i < args.length; i++) {
            if ("--workspace".equals(args[i]) && i + 1 < args.length) workspace = Path.of(args[++i]);
            if ("--direct".equals(args[i])) direct = true;
        }
        if (workspace == null) {
            System.err.println("[namdesktop-mcp] --workspace <path> is required");
            System.exit(1);
        }
        new NamMcpServer(workspace, direct).run();
    }

    NamMcpServer(Path workspacePath) {
        this(workspacePath, false);
    }

    NamMcpServer(Path workspacePath, boolean directMode) {
        this.workspacePath = workspacePath;
        this.directMode    = directMode;
    }

    public static Path directSentinelPath(Path workspacePath) {
        return workspacePath.resolveSibling(DIRECT_SENTINEL);
    }

    // -------------------------------------------------------------------------
    // Stdio loop
    // -------------------------------------------------------------------------

    private void run() throws IOException {
        if (directMode) {
            var sentinel = directSentinelPath(workspacePath);
            Files.createDirectories(sentinel.getParent());
            Files.writeString(sentinel, String.valueOf(ProcessHandle.current().pid()));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { Files.deleteIfExists(sentinel); } catch (IOException ignored) {}
            }));
            System.err.println("[namdesktop-mcp] direct mode — writing to " + workspacePath);
        }

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
        tools.add(tool("list_backlog",
                "List all actions with status BACKLOG across the whole workspace.",
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
        tools.add(tool("list_resources",
                "List all resources (links, files, notes) attached to a node.",
                schema(Map.of("node_id", prop("string", "UUID of the node")), List.of("node_id"))));
        tools.add(tool("add_resource",
                "Attach a resource to a node. type must be URI, EMAIL, FILE, or TEXT. Requires monitoring mode.",
                schema(Map.of(
                        "node_id",     prop("string", "UUID of the node"),
                        "type",        prop("string", "Resource type: URI, EMAIL, FILE, or TEXT"),
                        "value",       prop("string", "The resource value (URL, email address, file path, or plain text)"),
                        "description", prop("string", "Optional short note shown as tooltip")
                ), List.of("node_id", "type", "value"))));
        tools.add(tool("remove_resource",
                "Remove a resource from a node by its zero-based index. Requires monitoring mode.",
                schema(Map.of(
                        "node_id", prop("string", "UUID of the node"),
                        "index",   prop("integer", "Zero-based index of the resource to remove")
                ), List.of("node_id", "index"))));
        tools.add(tool("update_node",
                "Update editable properties of an existing node. All fields optional — only provided fields are changed. Requires monitoring mode.",
                schema(Map.of(
                        "node_id",     prop("string", "UUID of the node to update"),
                        "title",       prop("string", "New title (omit to keep existing)"),
                        "description", prop("string", "New description (omit to keep, empty string to clear)"),
                        "tags",        prop("array",  "New tag list — replaces current tags wholesale (omit to keep existing, empty array to clear)")
                ), List.of("node_id"))));
        tools.add(tool("edit_resource",
                "Update the value and/or description of an existing resource in place. Requires monitoring mode.",
                schema(Map.of(
                        "node_id",     prop("string",  "UUID of the node"),
                        "index",       prop("integer", "Zero-based index of the resource to edit"),
                        "value",       prop("string",  "New value (omit to keep existing)"),
                        "description", prop("string",  "New description/note (omit to keep existing, empty string to clear)")
                ), List.of("node_id", "index"))));
        tools.add(tool("add_blocked_by",
                "Add a blocking relationship to an existing node. Requires monitoring mode.",
                schema(Map.of(
                        "node_id",       prop("string", "UUID of the node that is blocked"),
                        "blocked_by_id", prop("string", "UUID of the node that blocks it")
                ), List.of("node_id", "blocked_by_id"))));
        tools.add(tool("remove_blocked_by",
                "Remove a blocking relationship from an existing node. Requires monitoring mode.",
                schema(Map.of(
                        "node_id",       prop("string", "UUID of the blocked node"),
                        "blocked_by_id", prop("string", "UUID of the blocker to remove")
                ), List.of("node_id", "blocked_by_id"))));
        tools.add(tool("move_node",
                "Move a node to a new parent within the project forest. Projects may be moved to any project or to top-level; actions may be moved between projects. Requires monitoring mode.",
                schema(Map.of(
                        "node_id",       prop("string", "UUID of the node to move"),
                        "new_parent_id", prop("string", "UUID of the new parent project, or omit / null for top-level (projects only)")
                ), List.of("node_id"))));
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
            case "list_backlog"          -> toolListBacklog();
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
            case "update_node"          -> toolUpdateNode(args);
            case "list_resources"        -> toolListResources(args);
            case "add_resource"         -> toolAddResource(args);
            case "remove_resource"      -> toolRemoveResource(args);
            case "edit_resource"        -> toolEditResource(args);
            case "add_blocked_by"       -> toolAddBlockedBy(args);
            case "remove_blocked_by"    -> toolRemoveBlockedBy(args);
            case "move_node"            -> toolMoveNode(args);
            default                      -> textResult("Unknown tool: " + name, true);
        };
    }

    // -------------------------------------------------------------------------
    // Read tools
    // -------------------------------------------------------------------------

    private Path readPath() {
        if (directMode) return workspacePath;
        return MonitoringMode.isActive(workspacePath)
                ? MonitoringMode.externalPath(workspacePath)
                : workspacePath;
    }

    private Path writePath() {
        return directMode ? workspacePath : MonitoringMode.externalPath(workspacePath);
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
        return toolListByStatus(NodeStatus.NEXT);
    }

    private ObjectNode toolListBacklog() throws IOException {
        return toolListByStatus(NodeStatus.BACKLOG);
    }

    private ObjectNode toolListByStatus(NodeStatus status) throws IOException {
        var ws    = repo.load(readPath());
        var items = MAPPER.createArrayNode();
        ws.getNodes().values().stream()
                .filter(n -> n.getStatus() == status)
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
        if (directMode)
            return textResult("Running in direct mode — writes go straight to workspace.json. No Swing app required.", false);
        var active = MonitoringMode.isActive(workspacePath);
        return textResult(active
                ? "Monitoring mode is ACTIVE. NamDesktop is watching for changes to workspace.external.json."
                : "Monitoring mode is OFF. Enable it in NamDesktop with Cmd+Shift+M before writing.", false);
    }

    // -------------------------------------------------------------------------
    // Write tools
    // -------------------------------------------------------------------------

    private ObjectNode toolAddInboxItem(JsonNode args) throws IOException {
        if (!directMode && !MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
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
        if (!directMode && !MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
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
        if (!directMode && !MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
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
        if (!directMode && !MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
        var title = args.path("title").asText("").strip();
        if (title.isEmpty()) return textResult("Error: title is required.", true);
        var projectIdStr = args.path("project_id").asText("").strip();
        if (projectIdStr.isEmpty()) return textResult("Error: project_id is required.", true);

        UUID projectId;
        try { projectId = UUID.fromString(projectIdStr); }
        catch (IllegalArgumentException e) { return textResult("Error: invalid project_id UUID.", true); }

        var ws = repo.load(writePath());
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
        if (!directMode && !MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
        var idStr = args.path("node_id").asText("").strip();
        if (idStr.isEmpty()) return textResult("Error: node_id is required.", true);

        UUID id;
        try { id = UUID.fromString(idStr); }
        catch (IllegalArgumentException e) { return textResult("Error: invalid node_id UUID.", true); }

        var ws   = repo.load(writePath());
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

    private ObjectNode toolUpdateNode(JsonNode args) throws IOException {
        if (!directMode && !MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
        var idStr = args.path("node_id").asText("").strip();
        if (idStr.isEmpty()) return textResult("Error: node_id is required.", true);
        if (!args.hasNonNull("title") && !args.hasNonNull("description") && !args.hasNonNull("tags"))
            return textResult("Error: at least one of title, description, or tags must be provided.", true);

        UUID id;
        try { id = UUID.fromString(idStr); }
        catch (IllegalArgumentException e) { return textResult("Error: invalid node_id UUID.", true); }

        var ws = repo.load(writePath());
        if (ws.getNode(id).isEmpty()) return textResult("Error: no node found with id " + idStr, true);

        final UUID   finalId   = id;
        final String newTitle  = args.hasNonNull("title")       ? args.path("title").asText().strip()       : null;
        final String newDesc   = args.hasNonNull("description")  ? args.path("description").asText()         : null;
        final JsonNode tagsNode = args.hasNonNull("tags")        ? args.path("tags")                          : null;

        if (newTitle != null && newTitle.isEmpty())
            return textResult("Error: title must not be blank.", true);

        atomicWrite(w -> w.getNode(finalId).ifPresent(n -> {
            if (newTitle != null)  n.setTitle(newTitle);
            if (newDesc  != null)  n.setDescription(newDesc.isBlank() ? null : newDesc);
            if (tagsNode != null) {
                var list = new ArrayList<String>();
                tagsNode.forEach(t -> { var tag = t.asText().strip(); if (!tag.isBlank()) list.add(tag); });
                n.setTags(list);
            }
        }));
        return textResult("Updated node \"" + ws.getNode(id).get().getTitle() + "\".", false);
    }

    private ObjectNode toolSetStatus(JsonNode args, NodeStatus status) throws IOException {
        if (!directMode && !MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
        var idStr = args.path("node_id").asText("").strip();
        if (idStr.isEmpty()) return textResult("Error: node_id is required.", true);

        java.util.UUID id;
        try { id = java.util.UUID.fromString(idStr); }
        catch (IllegalArgumentException e) { return textResult("Error: invalid node_id UUID.", true); }

        final java.util.UUID finalId = id;
        var ws = repo.load(writePath());
        var node = ws.getNode(finalId).orElse(null);
        if (node == null) return textResult("Error: no node found with id " + idStr, true);
        var oldStatus = node.getStatus();

        atomicWrite(w -> w.getNode(finalId).ifPresent(n -> n.setStatus(status)));
        return textResult("Node \"" + node.getTitle() + "\" status changed from "
                + oldStatus.name() + " to " + status.name() + ".", false);
    }

    private ObjectNode toolListResources(JsonNode args) throws IOException {
        var idStr = args.path("node_id").asText("").strip();
        if (idStr.isEmpty()) return textResult("Error: node_id is required.", true);
        UUID id;
        try { id = UUID.fromString(idStr); }
        catch (IllegalArgumentException e) { return textResult("Error: invalid node_id UUID.", true); }

        var ws   = repo.load(readPath());
        var node = ws.getNode(id).orElse(null);
        if (node == null) return textResult("Error: no node found with id " + idStr, true);

        var arr = MAPPER.createArrayNode();
        var resources = node.getResources();
        for (int i = 0; i < resources.size(); i++) {
            var res = resources.get(i);
            var o = arr.addObject();
            o.put("index", i);
            o.put("type", res.getType().name());
            o.put("value", res.getValue());
            if (res.getDescription() != null && !res.getDescription().isBlank())
                o.put("description", res.getDescription());
        }
        return textResult(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(arr), false);
    }

    private ObjectNode toolAddResource(JsonNode args) throws IOException {
        if (!directMode && !MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
        var idStr = args.path("node_id").asText("").strip();
        if (idStr.isEmpty()) return textResult("Error: node_id is required.", true);
        var typeStr = args.path("type").asText("").strip().toUpperCase();
        if (typeStr.isEmpty()) return textResult("Error: type is required.", true);
        var value = args.path("value").asText("").strip();
        if (value.isEmpty()) return textResult("Error: value is required.", true);

        UUID id;
        try { id = UUID.fromString(idStr); }
        catch (IllegalArgumentException e) { return textResult("Error: invalid node_id UUID.", true); }

        ResourceType type;
        try { type = ResourceType.valueOf(typeStr); }
        catch (IllegalArgumentException e) { return textResult("Error: invalid type \"" + typeStr + "\". Use URI, EMAIL, FILE, or TEXT.", true); }

        var ws = repo.load(writePath());
        if (ws.getNode(id).isEmpty()) return textResult("Error: no node found with id " + idStr, true);

        var desc = args.hasNonNull("description") ? args.path("description").asText().strip() : null;
        if (desc != null && desc.isBlank()) desc = null;

        final UUID finalId = id;
        final ResourceType finalType = type;
        final String finalDesc = desc;
        atomicWrite(w -> w.getNode(finalId).ifPresent(n -> n.getResources().add(new Resource(finalType, value, finalDesc))));
        return textResult("Added " + typeStr + " resource to node \"" + ws.getNode(id).get().getTitle() + "\".", false);
    }

    private ObjectNode toolRemoveResource(JsonNode args) throws IOException {
        if (!directMode && !MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
        var idStr = args.path("node_id").asText("").strip();
        if (idStr.isEmpty()) return textResult("Error: node_id is required.", true);
        if (!args.hasNonNull("index")) return textResult("Error: index is required.", true);
        var index = args.path("index").asInt(-1);
        if (index < 0) return textResult("Error: index must be a non-negative integer.", true);

        UUID id;
        try { id = UUID.fromString(idStr); }
        catch (IllegalArgumentException e) { return textResult("Error: invalid node_id UUID.", true); }

        var ws   = repo.load(writePath());
        var node = ws.getNode(id).orElse(null);
        if (node == null) return textResult("Error: no node found with id " + idStr, true);
        if (index >= node.getResources().size())
            return textResult("Error: index " + index + " out of range — node has " + node.getResources().size() + " resource(s).", true);

        var removed = node.getResources().get(index);
        final UUID finalId = id;
        final int finalIndex = index;
        atomicWrite(w -> w.getNode(finalId).ifPresent(n -> n.getResources().remove(finalIndex)));
        return textResult("Removed resource at index " + index + " (" + removed.getType().name() + ": " + removed.getValue() + ").", false);
    }

    private ObjectNode toolEditResource(JsonNode args) throws IOException {
        if (!directMode && !MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
        var idStr = args.path("node_id").asText("").strip();
        if (idStr.isEmpty()) return textResult("Error: node_id is required.", true);
        if (!args.hasNonNull("index")) return textResult("Error: index is required.", true);
        var index = args.path("index").asInt(-1);
        if (index < 0) return textResult("Error: index must be a non-negative integer.", true);
        if (!args.hasNonNull("value") && !args.hasNonNull("description"))
            return textResult("Error: at least one of value or description must be provided.", true);

        UUID id;
        try { id = UUID.fromString(idStr); }
        catch (IllegalArgumentException e) { return textResult("Error: invalid node_id UUID.", true); }

        var ws   = repo.load(writePath());
        var node = ws.getNode(id).orElse(null);
        if (node == null) return textResult("Error: no node found with id " + idStr, true);
        if (index >= node.getResources().size())
            return textResult("Error: index " + index + " out of range — node has " + node.getResources().size() + " resource(s).", true);

        final UUID   finalId    = id;
        final int    finalIndex = index;
        final String newValue   = args.hasNonNull("value") ? args.path("value").asText().strip() : null;
        final String newDesc    = args.hasNonNull("description") ? args.path("description").asText() : null;

        atomicWrite(w -> w.getNode(finalId).ifPresent(n -> {
            var r = n.getResources().get(finalIndex);
            if (newValue != null && !newValue.isEmpty()) r.setValue(newValue);
            if (newDesc  != null)                        r.setDescription(newDesc.isBlank() ? null : newDesc);
        }));
        return textResult("Updated resource at index " + index + " on node \"" + node.getTitle() + "\".", false);
    }

    private ObjectNode toolAddBlockedBy(JsonNode args) throws IOException {
        if (!directMode && !MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
        var nodeIdStr   = args.path("node_id").asText("").strip();
        var blockerStr  = args.path("blocked_by_id").asText("").strip();
        if (nodeIdStr.isEmpty())  return textResult("Error: node_id is required.", true);
        if (blockerStr.isEmpty()) return textResult("Error: blocked_by_id is required.", true);

        UUID nodeId, blockerId;
        try { nodeId   = UUID.fromString(nodeIdStr);  } catch (IllegalArgumentException e) { return textResult("Error: invalid node_id UUID.", true); }
        try { blockerId = UUID.fromString(blockerStr); } catch (IllegalArgumentException e) { return textResult("Error: invalid blocked_by_id UUID.", true); }

        var ws = repo.load(writePath());
        if (ws.getNode(nodeId).isEmpty())   return textResult("Error: no node found with node_id " + nodeIdStr, true);
        if (ws.getNode(blockerId).isEmpty()) return textResult("Error: no node found with blocked_by_id " + blockerStr, true);
        if (nodeId.equals(blockerId))        return textResult("Error: a node cannot block itself.", true);

        var result = atomicWriteWithResult(w -> {
            var node = w.getNode(nodeId).orElseThrow();
            if (node.getBlockedBy().contains(blockerId)) return null;
            if (wouldCreateBlockedByCycle(w, nodeId, blockerId))
                return "Error: adding this relationship would create a cycle.";
            node.getBlockedBy().add(blockerId);
            return null;
        });
        if (result != null) return textResult(result, true);
        var blocker = ws.getNode(blockerId).get();
        return textResult("Node \"" + ws.getNode(nodeId).get().getTitle() + "\" is now blocked by \"" + blocker.getTitle() + "\".", false);
    }

    private ObjectNode toolRemoveBlockedBy(JsonNode args) throws IOException {
        if (!directMode && !MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
        var nodeIdStr  = args.path("node_id").asText("").strip();
        var blockerStr = args.path("blocked_by_id").asText("").strip();
        if (nodeIdStr.isEmpty())  return textResult("Error: node_id is required.", true);
        if (blockerStr.isEmpty()) return textResult("Error: blocked_by_id is required.", true);

        UUID nodeId, blockerId;
        try { nodeId    = UUID.fromString(nodeIdStr);  } catch (IllegalArgumentException e) { return textResult("Error: invalid node_id UUID.", true); }
        try { blockerId = UUID.fromString(blockerStr); } catch (IllegalArgumentException e) { return textResult("Error: invalid blocked_by_id UUID.", true); }

        var ws = repo.load(writePath());
        if (ws.getNode(nodeId).isEmpty()) return textResult("Error: no node found with node_id " + nodeIdStr, true);

        final UUID finalBlockerId = blockerId;
        atomicWrite(w -> w.getNode(nodeId).ifPresent(n -> n.getBlockedBy().remove(finalBlockerId)));
        return textResult("Removed blocking relationship from \"" + ws.getNode(nodeId).get().getTitle() + "\".", false);
    }

    private boolean wouldCreateBlockedByCycle(NamWorkspace ws, UUID nodeId, UUID newBlockerId) {
        var visited = new HashSet<UUID>();
        var stack   = new ArrayDeque<UUID>();
        stack.push(newBlockerId);
        while (!stack.isEmpty()) {
            var current = stack.pop();
            if (current.equals(nodeId)) return true;
            if (!visited.add(current)) continue;
            ws.getNode(current).ifPresent(n -> n.getBlockedBy().forEach(stack::push));
        }
        return false;
    }

    private ObjectNode toolMoveNode(JsonNode args) throws IOException {
        if (!directMode && !MonitoringMode.isActive(workspacePath)) return textResult(NOT_MONITORING, false);
        var nodeIdStr     = args.path("node_id").asText("").strip();
        var newParentStr  = args.path("new_parent_id").asText("").strip();
        if (nodeIdStr.isEmpty()) return textResult("Error: node_id is required.", true);

        UUID nodeId;
        try { nodeId = UUID.fromString(nodeIdStr); } catch (IllegalArgumentException e) { return textResult("Error: invalid node_id UUID.", true); }

        var ws = repo.load(writePath());
        var node = ws.getNode(nodeId).orElse(null);
        if (node == null) return textResult("Error: no node found with node_id " + nodeIdStr, true);

        var structuralIds = Set.of(ws.getRootNodeId(), ws.getInboxNodeId(),
                ws.getProjectsNodeId(), ws.getNextActionsNodeId());
        if (structuralIds.contains(nodeId))
            return textResult("Error: structural nodes cannot be moved.", true);

        UUID newParentId;
        if (newParentStr.isEmpty()) {
            newParentId = node.isProject() ? ws.getProjectsNodeId() : ws.getNextActionsNodeId();
        } else {
            try { newParentId = UUID.fromString(newParentStr); } catch (IllegalArgumentException e) { return textResult("Error: invalid new_parent_id UUID.", true); }
            var newParent = ws.getNode(newParentId).orElse(null);
            if (newParent == null) return textResult("Error: no node found with new_parent_id " + newParentStr, true);
            var validStructural = node.isProject() ? ws.getProjectsNodeId() : ws.getNextActionsNodeId();
            if (structuralIds.contains(newParentId) && !newParentId.equals(validStructural))
                return textResult("Error: invalid target parent.", true);
            if (!node.isProject() && !newParent.isProject() && !newParentId.equals(ws.getNextActionsNodeId()))
                return textResult("Error: actions can only be moved into project nodes or the free actions area. Use 'Make project' on the target node first.", true);
        }

        if (nodeId.equals(newParentId)) return textResult("Error: a node cannot be its own parent.", true);
        var subtree = ws.collectSubtree(nodeId);
        if (subtree.contains(newParentId)) return textResult("Error: cannot move a node into one of its own descendants.", true);

        final UUID finalNewParentId = newParentId;
        atomicWrite(w -> {
            w.getNodes().values().forEach(n -> n.getChildIds().remove(nodeId));
            w.getNode(finalNewParentId).ifPresent(p -> p.getChildIds().add(nodeId));
        });
        String parentTitle;
        if (newParentId.equals(ws.getProjectsNodeId()))       parentTitle = "top-level projects";
        else if (newParentId.equals(ws.getNextActionsNodeId())) parentTitle = "free actions";
        else                                                    parentTitle = ws.getNode(newParentId).get().getTitle();
        return textResult("Moved \"" + node.getTitle() + "\" to \"" + parentTitle + "\".", false);
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
        var target  = writePath();
        var tmpPath = target.resolveSibling(target.getFileName() + ".tmp");
        var ws      = repo.load(target);
        var error   = mutate.apply(ws);
        if (error != null) return error;
        repo.save(tmpPath, ws);
        try {
            Files.move(tmpPath, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmpPath, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return null;
    }

    private void atomicWrite(java.util.function.Consumer<NamWorkspace> mutate) throws IOException {
        var target  = writePath();
        var tmpPath = target.resolveSibling(target.getFileName() + ".tmp");
        var ws      = repo.load(target);
        mutate.accept(ws);
        repo.save(tmpPath, ws);
        try {
            Files.move(tmpPath, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmpPath, target, StandardCopyOption.REPLACE_EXISTING);
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
