package namdesktop.demo;

import namdesktop.model.NodeStatus;
import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;
import swingdemo.ScriptRunner;

import java.util.Map;

/**
 * Registers NamDesktop assertion handlers on a {@link ScriptRunner}.
 *
 * <p>Each handler throws {@link IllegalStateException} on failure with a descriptive
 * message. The ScriptRunner catches it, logs to stderr, and increments its
 * failure count — making the failure visible to the e2e exit-code gate.
 */
public final class NamAssertWiring {

    private final NamWorkspace        workspace;
    private final NamWorkspaceService service;

    public NamAssertWiring(NamWorkspace workspace, NamWorkspaceService service) {
        this.workspace = workspace;
        this.service   = service;
    }

    public void configure(ScriptRunner runner) {
        runner
            .register("assertNodeExists",      this::assertNodeExists)
            .register("assertNodeNotExists",   this::assertNodeNotExists)
            .register("assertNodeStatus",      this::assertNodeStatus)
            .register("assertTagOnNode",       this::assertTagOnNode)
            .register("assertProjectExists",   this::assertProjectExists)
            .register("assertSavedViewExists", this::assertSavedViewExists)
            .register("assertNodeCount",       this::assertNodeCount)
            .register("assertIsBlocked",       this::assertIsBlocked)
            .register("assertNotBlocked",      this::assertNotBlocked);
    }

    private void assertNodeExists(Map<String, Object> args) {
        var title = str(args, "title");
        var found = workspace.getNodes().values().stream()
                .anyMatch(n -> title.equals(n.getTitle()));
        if (!found) throw new IllegalStateException("Expected node with title \"" + title + "\" but it was not found");
    }

    private void assertNodeNotExists(Map<String, Object> args) {
        var title = str(args, "title");
        var found = workspace.getNodes().values().stream()
                .anyMatch(n -> title.equals(n.getTitle()));
        if (found) throw new IllegalStateException("Expected node with title \"" + title + "\" to be absent but it was found");
    }

    private void assertNodeStatus(Map<String, Object> args) {
        var title    = str(args, "title");
        var expected = NodeStatus.valueOf(str(args, "status").toUpperCase());
        var node     = workspace.getNodes().values().stream()
                .filter(n -> title.equals(n.getTitle()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Node not found: \"" + title + "\""));
        if (node.getStatus() != expected) {
            throw new IllegalStateException("Node \"" + title + "\": expected status " + expected
                    + " but was " + node.getStatus());
        }
    }

    private void assertTagOnNode(Map<String, Object> args) {
        var title = str(args, "title");
        var tag   = str(args, "tag");
        var node  = workspace.getNodes().values().stream()
                .filter(n -> title.equals(n.getTitle()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Node not found: \"" + title + "\""));
        if (!node.getTags().contains(tag)) {
            throw new IllegalStateException("Node \"" + title + "\": expected tag \"" + tag
                    + "\" but tags were " + node.getTags());
        }
    }

    private void assertProjectExists(Map<String, Object> args) {
        var title = str(args, "title");
        var found = workspace.getNodes().values().stream()
                .anyMatch(n -> title.equals(n.getTitle()) && n.isProject());
        if (!found) throw new IllegalStateException("Expected project with title \"" + title + "\" but it was not found");
    }

    private void assertSavedViewExists(Map<String, Object> args) {
        var name  = str(args, "name");
        var found = workspace.getSavedViews().stream()
                .anyMatch(v -> name.equals(v.name()));
        if (!found) throw new IllegalStateException("Expected saved view \"" + name + "\" but it was not found");
    }

    private void assertNodeCount(Map<String, Object> args) {
        var expected = ((Number) args.get("count")).intValue();
        var structural = 4; // root, inbox, projects, nextActions
        var actual = workspace.getNodes().size() - structural;
        if (actual != expected) {
            throw new IllegalStateException("Expected " + expected + " non-structural nodes but found " + actual);
        }
    }

    private void assertIsBlocked(Map<String, Object> args) {
        var title = str(args, "title");
        var node  = workspace.getNodes().values().stream()
                .filter(n -> title.equals(n.getTitle()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Node not found: \"" + title + "\""));
        if (!service.isBlocked(node.getId()))
            throw new IllegalStateException("Expected \"" + title + "\" to be blocked but it is not");
    }

    private void assertNotBlocked(Map<String, Object> args) {
        var title = str(args, "title");
        var node  = workspace.getNodes().values().stream()
                .filter(n -> title.equals(n.getTitle()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Node not found: \"" + title + "\""));
        if (service.isBlocked(node.getId()))
            throw new IllegalStateException("Expected \"" + title + "\" to be unblocked but it is blocked");
    }

    private static String str(Map<String, Object> args, String key) {
        var val = args.get(key);
        if (val == null) throw new IllegalArgumentException("Missing arg: " + key);
        return val.toString();
    }
}
