package namdesktop.demo;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;
import swingdemo.ScriptRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registers NamDesktop-specific action handlers on a {@link ScriptRunner}.
 *
 * <p>Node lookup is by title (first match). Script args use human-readable
 * titles, not UUIDs, so scripts remain readable and portable.
 */
public final class NamDemoWiring {

    private final NamWorkspace        workspace;
    private final NamWorkspaceService service;

    public NamDemoWiring(NamWorkspace workspace, NamWorkspaceService service) {
        this.workspace = workspace;
        this.service   = service;
    }

    public void configure(ScriptRunner runner) {
        runner
            .register("addProject",      this::addProject)
            .register("addSubProject",   this::addSubProject)
            .register("addAction",       this::addAction)
            .register("addNextAction",   this::addNextAction)
            .register("addInboxItem",    this::addInboxItem)
            .register("markNext",        this::markNext)
            .register("markDone",        this::markDone)
            .register("markBacklog",     this::markBacklog)
            .register("addTag",          this::addTag)
            .register("createSavedView",    this::createSavedView)
            .register("deleteProject",      this::deleteProject)
            .register("addPrerequisite",    this::addPrerequisite)
            .register("removePrerequisite", this::removePrerequisite);
    }

    private void addProject(Map<String, Object> args) throws Exception {
        service.addSubProject(workspace.getProjectsNodeId(), str(args, "title"));
    }

    private void addSubProject(Map<String, Object> args) throws Exception {
        service.addSubProject(findByTitle(str(args, "parent")).getId(), str(args, "title"));
    }

    private void addAction(Map<String, Object> args) throws Exception {
        service.addChild(findByTitle(str(args, "project")).getId(), str(args, "title"));
    }

    private void addNextAction(Map<String, Object> args) throws Exception {
        service.createNextAction(str(args, "title"));
    }

    private void addInboxItem(Map<String, Object> args) throws Exception {
        service.addInboxItem(str(args, "title"));
    }

    private void markNext(Map<String, Object> args) throws Exception {
        service.markNext(findByTitle(str(args, "title")).getId());
    }

    private void markDone(Map<String, Object> args) throws Exception {
        service.markDone(findByTitle(str(args, "title")).getId());
    }

    private void markBacklog(Map<String, Object> args) throws Exception {
        service.markBacklog(findByTitle(str(args, "title")).getId());
    }

    private void addTag(Map<String, Object> args) throws Exception {
        var node = findByTitle(str(args, "title"));
        var tag  = str(args, "tag");
        var tags = new ArrayList<>(node.getTags());
        if (!tags.contains(tag)) tags.add(tag);
        service.updateTags(node.getId(), tags);
    }

    @SuppressWarnings("unchecked")
    private void createSavedView(Map<String, Object> args) throws Exception {
        var name     = str(args, "name");
        var tags     = args.containsKey("tags") ? (List<String>) args.get("tags") : List.<String>of();
        var nextOnly = args.containsKey("nextOnly") && (boolean) args.get("nextOnly");
        service.createSavedView(name, tags, nextOnly);
    }

    private void deleteProject(Map<String, Object> args) throws Exception {
        service.deleteRecursive(findByTitle(str(args, "title")).getId());
    }

    private void addPrerequisite(Map<String, Object> args) throws Exception {
        var blocked = findByTitle(str(args, "blocked"));
        var prereq  = findByTitle(str(args, "prereq"));
        service.addPrerequisite(blocked.getId(), prereq.getId());
    }

    private void removePrerequisite(Map<String, Object> args) throws Exception {
        var blocked = findByTitle(str(args, "blocked"));
        var prereq  = findByTitle(str(args, "prereq"));
        service.removePrerequisite(blocked.getId(), prereq.getId());
    }

    private NamNode findByTitle(String title) {
        return workspace.getNodes().values().stream()
                .filter(n -> title.equals(n.getTitle()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No node with title: \"" + title + "\""));
    }

    private static String str(Map<String, Object> args, String key) {
        var val = args.get(key);
        if (val == null) throw new IllegalArgumentException("Missing arg: " + key);
        return val.toString();
    }
}
