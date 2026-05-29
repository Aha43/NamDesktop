package namdesktop.service;

import namdesktop.model.NamWorkspace;
import namdesktop.persist.JsonWorkspaceRepository;

import java.io.IOException;
import java.nio.file.*;
import java.util.function.Consumer;

public final class ExternalWorkspaceWatcher {

    private final Path workspacePath;
    private final JsonWorkspaceRepository repo;
    private final Consumer<MonitoringMode.DiffSummary> onChanges;

    private volatile boolean running = false;
    private WatchService watchService;

    public ExternalWorkspaceWatcher(Path workspacePath, JsonWorkspaceRepository repo,
                                     Consumer<MonitoringMode.DiffSummary> onChanges) {
        this.workspacePath = workspacePath;
        this.repo          = repo;
        this.onChanges     = onChanges;
    }

    public void start(NamWorkspace initialExternal) {
        running = true;
        var thread = new Thread(() -> watch(initialExternal), "external-workspace-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (watchService != null) {
            try { watchService.close(); } catch (IOException ignored) {}
        }
    }

    private void watch(NamWorkspace initialExternal) {
        var extPath  = MonitoringMode.externalPath(workspacePath);
        var dir      = workspacePath.getParent();
        var filename = extPath.getFileName().toString();
        NamWorkspace lastKnown = initialExternal;

        try {
            watchService = FileSystems.getDefault().newWatchService();
            dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);

            while (running) {
                WatchKey key;
                try {
                    key = watchService.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (key == null) continue;

                boolean relevant = false;
                for (var event : key.pollEvents()) {
                    var ctx = event.context();
                    if (ctx instanceof Path p && p.getFileName().toString().equals(filename)) {
                        relevant = true;
                    }
                }
                key.reset();

                if (!relevant || !running) continue;

                try {
                    var updated = repo.load(extPath);
                    var summary = MonitoringMode.diff(lastKnown, updated);
                    lastKnown = updated;
                    if (!summary.isEmpty()) onChanges.accept(summary);
                } catch (IOException ignored) {
                    // unparseable or mid-write — wait for next event
                }
            }
        } catch (IOException ignored) {
            // WatchService unavailable or closed on stop()
        }
    }
}
