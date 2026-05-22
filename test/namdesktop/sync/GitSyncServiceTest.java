package namdesktop.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GitSyncServiceTest {

    @TempDir Path tempDir;

    @Test
    void isConfigured_falseWhenBlank() {
        assertFalse(new GitSyncService("",   tempDir).isConfigured());
        assertFalse(new GitSyncService("  ", tempDir).isConfigured());
        assertFalse(new GitSyncService(null, tempDir).isConfigured());
    }

    @Test
    void isConfigured_trueWhenUrlPresent() {
        assertTrue(new GitSyncService("https://github.com/user/repo", tempDir).isConfigured());
    }

    @Test
    void push_throwsWhenNotConfigured() {
        var svc = new GitSyncService("", tempDir);
        // push on an unconfigured service should fail fast (no git clone attempted)
        var ex = assertThrows(IOException.class, () -> svc.push(tempDir.resolve("workspace.json")));
        assertNotNull(ex);
    }

    @Test
    void pull_throwsWhenNotConfigured() {
        var svc = new GitSyncService("", tempDir);
        assertThrows(IOException.class, () -> svc.pull(tempDir.resolve("workspace.json")));
    }
}
