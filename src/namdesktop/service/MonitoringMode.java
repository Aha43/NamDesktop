package namdesktop.service;

import namdesktop.model.NamWorkspace;
import namdesktop.persist.JsonWorkspaceRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Set;

public final class MonitoringMode {

    private MonitoringMode() {}

    public static Path externalPath(Path workspacePath) {
        return workspacePath.resolveSibling("workspace.external.json");
    }

    public static Path sentinelPath(Path workspacePath) {
        return workspacePath.resolveSibling(".namdesktop-monitoring");
    }

    public static boolean isActive(Path workspacePath) {
        return workspacePath != null && Files.exists(sentinelPath(workspacePath));
    }

    public static void enter(Path workspacePath) throws IOException {
        Files.copy(workspacePath, externalPath(workspacePath), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(sentinelPath(workspacePath), "monitoring");
    }

    public static ExitResult exit(Path workspacePath, JsonWorkspaceRepository repo) {
        var extPath = externalPath(workspacePath);
        if (!Files.exists(extPath)) return new ExitResult.NoChanges();
        try {
            var base     = repo.load(workspacePath);
            var external = repo.load(extPath);
            var summary  = diff(base, external);
            return summary.isEmpty() ? new ExitResult.NoChanges() : new ExitResult.HasChanges(summary);
        } catch (IOException e) {
            return new ExitResult.Unparseable(e.getMessage());
        }
    }

    public static void accept(Path workspacePath) throws IOException {
        Files.move(externalPath(workspacePath), workspacePath, StandardCopyOption.REPLACE_EXISTING);
        deleteQuietly(sentinelPath(workspacePath));
    }

    public static void reject(Path workspacePath) {
        deleteQuietly(externalPath(workspacePath));
        deleteQuietly(sentinelPath(workspacePath));
    }

    /** Flush external → main and reset external to new baseline; sentinel stays (monitoring continues). */
    public static void checkpointAccept(Path workspacePath) throws IOException {
        Files.copy(externalPath(workspacePath), workspacePath, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(workspacePath, externalPath(workspacePath), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Reset external to current main (discard external changes); sentinel stays (monitoring continues). */
    public static void checkpointReject(Path workspacePath) throws IOException {
        Files.copy(workspacePath, externalPath(workspacePath), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) {}
    }

    static DiffSummary diff(NamWorkspace base, NamWorkspace external) {
        var structural = Set.of(
                base.getRootNodeId(), base.getInboxNodeId(),
                base.getProjectsNodeId(), base.getNextActionsNodeId());

        int inboxAdded = 0, projectsCreated = 0, actionsAdded = 0, statusChanged = 0, deleted = 0, resourcesChanged = 0;

        for (var entry : external.getNodes().entrySet()) {
            var id      = entry.getKey();
            var extNode = entry.getValue();
            if (structural.contains(id)) continue;
            var baseNode = base.getNode(id).orElse(null);
            if (baseNode == null) {
                if (extNode.isProject()) {
                    projectsCreated++;
                } else {
                    var parent = external.getParent(id);
                    if (parent.isPresent() && parent.get().getId().equals(external.getInboxNodeId()))
                        inboxAdded++;
                    else
                        actionsAdded++;
                }
            } else {
                if (baseNode.getStatus() != extNode.getStatus()) statusChanged++;
                if (!baseNode.getResources().equals(extNode.getResources())) resourcesChanged++;
            }
        }

        for (var id : base.getNodes().keySet()) {
            if (structural.contains(id)) continue;
            if (!external.getNodes().containsKey(id)) deleted++;
        }

        return new DiffSummary(inboxAdded, projectsCreated, actionsAdded, statusChanged, deleted, resourcesChanged);
    }

    public sealed interface ExitResult {
        record NoChanges()                      implements ExitResult {}
        record HasChanges(DiffSummary summary)  implements ExitResult {}
        record Unparseable(String error)        implements ExitResult {}
    }

    public record DiffSummary(int inboxAdded, int projectsCreated, int actionsAdded,
                               int statusChanged, int deleted, int resourcesChanged) {

        public boolean isEmpty() {
            return inboxAdded == 0 && projectsCreated == 0 && actionsAdded == 0
                    && statusChanged == 0 && deleted == 0 && resourcesChanged == 0;
        }

        public String describe() {
            var parts = new ArrayList<String>();
            if (inboxAdded       > 0) parts.add(inboxAdded       + (inboxAdded       == 1 ? " item added to Inbox"    : " items added to Inbox"));
            if (projectsCreated  > 0) parts.add(projectsCreated  + (projectsCreated  == 1 ? " project created"        : " projects created"));
            if (actionsAdded     > 0) parts.add(actionsAdded     + (actionsAdded     == 1 ? " action added"           : " actions added"));
            if (statusChanged    > 0) parts.add(statusChanged    + (statusChanged    == 1 ? " status change"          : " status changes"));
            if (deleted          > 0) parts.add(deleted          + (deleted          == 1 ? " item deleted"           : " items deleted"));
            if (resourcesChanged > 0) parts.add(resourcesChanged + (resourcesChanged == 1 ? " resource change"        : " resource changes"));
            return String.join(", ", parts);
        }
    }
}
