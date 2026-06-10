package namdesktop.sync;

import namdesktop.app.CloudSyncSettings;
import namdesktop.model.NamWorkspace;
import namdesktop.persist.JsonWorkspaceRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SupabaseSyncServiceTest {

    private static final String SIGN_IN_OK =
            "{\"access_token\":\"jwt-1\",\"user\":{\"id\":\"uid-1\"}}";

    /** Records every call and replays canned responses in order. */
    private static final class FakeTransport implements SupabaseSyncService.Transport {
        record Call(String method, String url, Map<String, String> headers, String body) {}
        final List<Call> calls = new ArrayList<>();
        final List<SupabaseSyncService.Response> responses = new ArrayList<>();

        @Override
        public SupabaseSyncService.Response send(String method, String url,
                                                 Map<String, String> headers, String body) {
            calls.add(new Call(method, url, headers, body));
            return responses.remove(0);
        }

        Call call(int i) { return calls.get(i); }
    }

    private static CloudSyncSettings settings(long lastSyncedVersion) {
        var s = new CloudSyncSettings();
        s.setEnabled(true);
        s.setEmail("test@namdesktop.local");
        s.setPassword("pw");
        s.setLastSyncedVersion(lastSyncedVersion);
        return s;
    }

    private static SupabaseSyncService.Response ok(String body) {
        return new SupabaseSyncService.Response(200, body);
    }

    @Test
    void pushPatchesWithVersionGuardAndStoresNewVersion() {
        var t = new FakeTransport();
        t.responses.add(ok(SIGN_IN_OK));
        t.responses.add(ok("[{\"version\":4}]"));
        var settings = settings(3);

        var result = new SupabaseSyncService(t).push(NamWorkspace.createDefault(), settings);

        assertTrue(result.success());
        assertEquals(4, result.remoteVersion());
        assertEquals(4, settings.getLastSyncedVersion());

        var patch = t.call(1);
        assertEquals("PATCH", patch.method());
        assertTrue(patch.url().contains("owner_user_id=eq.uid-1"));
        assertTrue(patch.url().contains("name=eq.default"));
        assertTrue(patch.url().contains("version=eq.3"));
        assertEquals("Bearer jwt-1", patch.headers().get("Authorization"));
        assertEquals("return=representation", patch.headers().get("Prefer"));
        assertTrue(patch.body().contains("\"version\":4"));
        assertTrue(patch.body().contains("\"document\""));
    }

    @Test
    void pushReportsConflictWhenGuardMatchesNothingAndRowExists() {
        var t = new FakeTransport();
        t.responses.add(ok(SIGN_IN_OK));
        t.responses.add(ok("[]"));                  // guarded PATCH: zero rows
        t.responses.add(ok("[{\"version\":7}]"));   // row exists at version 7
        var settings = settings(3);

        var result = new SupabaseSyncService(t).push(NamWorkspace.createDefault(), settings);

        assertFalse(result.success());
        assertTrue(result.conflict());
        assertEquals(7, result.remoteVersion());
        assertEquals(3, settings.getLastSyncedVersion(), "conflict must not move the watermark");
    }

    @Test
    void firstPushInsertsRowWithoutPatching() {
        var t = new FakeTransport();
        t.responses.add(ok(SIGN_IN_OK));
        t.responses.add(ok("[]"));                  // no remote row
        t.responses.add(ok("[{\"version\":1}]"));   // insert returns created row
        var settings = settings(0);

        var result = new SupabaseSyncService(t).push(NamWorkspace.createDefault(), settings);

        assertTrue(result.success());
        assertEquals(1, settings.getLastSyncedVersion());
        assertEquals("GET",  t.call(1).method(), "never-synced push must not attempt a guarded PATCH");
        assertEquals("POST", t.call(2).method());
        assertTrue(t.call(2).body().contains("\"owner_user_id\":\"uid-1\""));
    }

    @Test
    void pushTreatsVanishedRemoteRowAsFirstPush() {
        var t = new FakeTransport();
        t.responses.add(ok(SIGN_IN_OK));
        t.responses.add(ok("[]"));                  // guarded PATCH: zero rows
        t.responses.add(ok("[]"));                  // ...because the row is gone
        t.responses.add(ok("[{\"version\":1}]"));   // re-insert
        var settings = settings(5);

        var result = new SupabaseSyncService(t).push(NamWorkspace.createDefault(), settings);

        assertTrue(result.success());
        assertEquals(1, settings.getLastSyncedVersion());
    }

    @Test
    void pushFailsWithReadableErrorOnBadCredentials() {
        var t = new FakeTransport();
        t.responses.add(new SupabaseSyncService.Response(400, "{\"error\":\"invalid\"}"));

        var result = new SupabaseSyncService(t).push(NamWorkspace.createDefault(), settings(3));

        assertFalse(result.success());
        assertFalse(result.conflict());
        assertTrue(result.error().contains("Sign-in failed"));
    }

    @Test
    void pullDeserializesDocumentAndStoresVersion() throws Exception {
        var pushed = NamWorkspace.createDefault();
        var document = new JsonWorkspaceRepository().toJson(pushed);
        var t = new FakeTransport();
        t.responses.add(ok(SIGN_IN_OK));
        t.responses.add(ok("[{\"version\":9,\"document\":" + document + "}]"));
        var settings = settings(2);

        var result = new SupabaseSyncService(t).pull(settings);

        assertTrue(result.success());
        assertEquals(9, result.remoteVersion());
        assertEquals(9, settings.getLastSyncedVersion());
        assertEquals(pushed.getRootNodeId(), result.workspace().getRootNodeId());
        assertEquals(pushed.getNodes().size(), result.workspace().getNodes().size());
    }

    @Test
    void pullWithNoRemoteRowIsNothingToPull() {
        var t = new FakeTransport();
        t.responses.add(ok(SIGN_IN_OK));
        t.responses.add(ok("[]"));
        var settings = settings(2);

        var result = new SupabaseSyncService(t).pull(settings);

        assertFalse(result.success());
        assertTrue(result.nothingToPull());
        assertNull(result.error());
        assertEquals(2, settings.getLastSyncedVersion());
    }

    @Test
    void pullFailsWithReadableErrorOnHttpError() {
        var t = new FakeTransport();
        t.responses.add(ok(SIGN_IN_OK));
        t.responses.add(new SupabaseSyncService.Response(500, "boom"));

        var result = new SupabaseSyncService(t).pull(settings(2));

        assertFalse(result.success());
        assertTrue(result.error().contains("HTTP 500"));
    }

    @Test
    void devWorkspacePushTargetsDevRowAndDevWatermark() {
        var t = new FakeTransport();
        t.responses.add(ok(SIGN_IN_OK));
        t.responses.add(ok("[]"));                  // no dev row yet
        t.responses.add(ok("[{\"version\":1}]"));   // insert dev row
        var settings = settings(3);                 // default workspace already at v3
        var service = new SupabaseSyncService(CloudSyncSettings.WORKSPACE_DEV, t);

        var result = service.push(NamWorkspace.createDefault(), settings);

        assertTrue(result.success());
        assertTrue(t.call(1).url().contains("name=eq.dev"));
        assertTrue(t.call(2).body().contains("\"name\":\"dev\""));
        assertEquals(1, settings.getLastSyncedVersionDev());
        assertEquals(3, settings.getLastSyncedVersion(), "dev push must not move the default watermark");
    }

    @Test
    void devWorkspacePullStoresOnlyDevWatermark() throws Exception {
        var document = new JsonWorkspaceRepository().toJson(NamWorkspace.createDefault());
        var t = new FakeTransport();
        t.responses.add(ok(SIGN_IN_OK));
        t.responses.add(ok("[{\"version\":6,\"document\":" + document + "}]"));
        var settings = settings(3);
        var service = new SupabaseSyncService(CloudSyncSettings.WORKSPACE_DEV, t);

        var result = service.pull(settings);

        assertTrue(result.success());
        assertTrue(t.call(1).url().contains("name=eq.dev"));
        assertEquals(6, settings.getLastSyncedVersionDev());
        assertEquals(3, settings.getLastSyncedVersion(), "dev pull must not move the default watermark");
    }

    @Test
    void defaultWorkspacePushLeavesDevWatermarkUntouched() {
        var t = new FakeTransport();
        t.responses.add(ok(SIGN_IN_OK));
        t.responses.add(ok("[{\"version\":4}]"));
        var settings = settings(3);
        settings.setLastSyncedVersionDev(9);

        var result = new SupabaseSyncService(t).push(NamWorkspace.createDefault(), settings);

        assertTrue(result.success());
        assertTrue(t.call(1).url().contains("name=eq.default"));
        assertEquals(4, settings.getLastSyncedVersion());
        assertEquals(9, settings.getLastSyncedVersionDev(), "default push must not move the dev watermark");
    }
}
