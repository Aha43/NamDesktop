package namdesktop.service;

import namdesktop.model.NamWorkspace;
import namdesktop.persist.JsonWorkspaceRepository;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.function.Consumer;

public final class ExternalWorkspaceWatcher {

    private static final long POLL_INTERVAL_MS = 500;

    private final Path workspacePath;
    private final JsonWorkspaceRepository repo;
    private final Consumer<MonitoringMode.DiffSummary> onChanges;

    private volatile boolean running = false;

    public ExternalWorkspaceWatcher(Path workspacePath, JsonWorkspaceRepository repo,
                                     Consumer<MonitoringMode.DiffSummary> onChanges) {
        this.workspacePath = workspacePath;
        this.repo          = repo;
        this.onChanges     = onChanges;
    }

    public void start(NamWorkspace initialExternal) {
        running = true;
        var thread = new Thread(() -> poll(initialExternal), "external-workspace-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
    }

    private void poll(NamWorkspace initialExternal) {
        var extPath   = MonitoringMode.externalPath(workspacePath);
        var lastKnown = initialExternal;
        var lastMtime = FileTime.fromMillis(0);

        while (running) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (!running || !Files.exists(extPath)) continue;

            try {
                var mtime = Files.getLastModifiedTime(extPath);
                if (!mtime.equals(lastMtime)) {
                    lastMtime = mtime;
                    var updated = repo.load(extPath);
                    var summary = MonitoringMode.diff(lastKnown, updated);
                    lastKnown = updated;
                    if (!summary.isEmpty()) onChanges.accept(summary);
                }
            } catch (IOException ignored) {
                // unparseable or mid-write — wait for next poll
            }
        }
    }
}
