package namdesktop.sync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class GitSyncService implements WorkspaceSyncService {

    private static final String WORKSPACE_FILE = "workspace.json";

    private final String repoUrl;
    private final Path   cloneDir;

    public GitSyncService(String repoUrl, Path cloneDir) {
        this.repoUrl  = repoUrl  != null ? repoUrl.strip() : "";
        this.cloneDir = cloneDir;
    }

    @Override
    public boolean isConfigured() { return !repoUrl.isEmpty(); }

    @Override
    public void push(Path workspacePath) throws IOException {
        if (!isConfigured()) throw new IOException("Sync not configured — set a GitHub repo URL in Settings.");
        ensureClone();
        Files.copy(workspacePath, cloneDir.resolve(WORKSPACE_FILE), StandardCopyOption.REPLACE_EXISTING);
        git("add", WORKSPACE_FILE);
        try {
            git("commit", "-m", "sync");
        } catch (IOException e) {
            // nothing to commit is fine — git exits 1 in that case
            if (!e.getMessage().contains("nothing to commit")) throw e;
        }
        git("push");
    }

    @Override
    public void pull(Path workspacePath) throws IOException {
        if (!isConfigured()) throw new IOException("Sync not configured — set a GitHub repo URL in Settings.");
        ensureClone();
        git("pull");
        var synced = cloneDir.resolve(WORKSPACE_FILE);
        if (!Files.exists(synced)) throw new IOException("No workspace.json found in remote repo.");
        Files.copy(synced, workspacePath, StandardCopyOption.REPLACE_EXISTING);
    }

    private void ensureClone() throws IOException {
        if (Files.exists(cloneDir.resolve(".git"))) return;
        Files.createDirectories(cloneDir.getParent());
        run(List.of("git", "clone", repoUrl, cloneDir.toString()));
    }

    private void git(String... args) throws IOException {
        var cmd = new java.util.ArrayList<String>();
        cmd.add("git");
        cmd.addAll(List.of("-C", cloneDir.toString()));
        cmd.addAll(List.of(args));
        run(cmd);
    }

    private static void run(List<String> cmd) throws IOException {
        var process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
        String output;
        try {
            output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Git command interrupted", e);
        }
        if (process.exitValue() != 0) {
            throw new IOException("git " + cmd.get(cmd.size() - 1) + " failed: " + output.strip());
        }
    }
}
