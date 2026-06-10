package namdesktop.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import namdesktop.app.CloudSyncSettings;
import namdesktop.model.NamWorkspace;
import namdesktop.persist.JsonWorkspaceRepository;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Supabase implementation of {@link CloudSyncService} — the PoC spike
 * (namdesktop.spike.SupabaseSpike) productionised. Signs in fresh per operation
 * (no token refresh; JWT lifetime far exceeds one push/pull). Optimistic locking:
 * PATCH guarded by version=eq.&lt;lastSyncedVersion&gt;; PostgREST returns 200 with
 * zero rows on a stale guard — never 409 — so conflict detection counts rows
 * returned under Prefer: return=representation.
 */
public final class SupabaseSyncService implements CloudSyncService {

    /** HTTP seam so unit tests can fake the wire without a server. */
    @FunctionalInterface
    public interface Transport {
        Response send(String method, String url, Map<String, String> headers, String body)
                throws IOException, InterruptedException;
    }

    public record Response(int status, String body) {}

    private static final String WORKSPACE_NAME = "default";

    private final Transport transport;
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonWorkspaceRepository repository = new JsonWorkspaceRepository();

    public SupabaseSyncService() { this(httpClientTransport()); }

    SupabaseSyncService(Transport transport) { this.transport = transport; }

    @Override
    public PushResult push(NamWorkspace workspace, CloudSyncSettings settings) {
        try {
            var auth = signIn(settings);
            var document = mapper.readTree(repository.toJson(workspace));
            var local = settings.getLastSyncedVersion();
            if (local > 0) {
                var rows = patchGuarded(settings, auth, local, document);
                if (rows.size() == 1) return pushed(settings, rows.get(0));
            }
            // Guarded update matched nothing (or never synced): first push or conflict?
            var existing = fetchRow(settings, auth);
            if (existing == null) {
                return pushed(settings, insert(settings, auth, document));
            }
            return PushResult.conflict(existing.path("version").asLong());
        } catch (SyncFailure e) {
            return PushResult.failure(e.getMessage());
        } catch (IOException e) {
            return PushResult.failure(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PushResult.failure("Sync interrupted");
        }
    }

    @Override
    public PullResult pull(CloudSyncSettings settings) {
        try {
            var auth = signIn(settings);
            var row = fetchRow(settings, auth);
            if (row == null) return PullResult.noRemote();
            var workspace = repository.fromJson(mapper.writeValueAsString(row.path("document")));
            var version = row.path("version").asLong();
            settings.setLastSyncedVersion(version);
            return PullResult.ok(workspace, version);
        } catch (SyncFailure e) {
            return PullResult.failure(e.getMessage());
        } catch (IOException e) {
            return PullResult.failure(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PullResult.failure("Sync interrupted");
        }
    }

    private record Auth(String token, String userId) {}

    private Auth signIn(CloudSyncSettings settings) throws IOException, InterruptedException {
        var body = mapper.createObjectNode();
        body.put("email", settings.getEmail());
        body.put("password", settings.getPassword());
        var response = transport.send("POST",
                settings.getSupabaseUrl() + "/auth/v1/token?grant_type=password",
                Map.of("apikey", settings.getPublishableKey(),
                       "Content-Type", "application/json"),
                body.toString());
        if (response.status() >= 300) {
            throw new SyncFailure("Sign-in failed (HTTP " + response.status() + ") — check email/password in Settings");
        }
        var json = mapper.readTree(response.body());
        var token = json.path("access_token").asText();
        var userId = json.path("user").path("id").asText();
        if (token.isEmpty() || userId.isEmpty()) throw new SyncFailure("Sign-in response missing token or user id");
        return new Auth(token, userId);
    }

    private JsonNode patchGuarded(CloudSyncSettings settings, Auth auth, long expectedVersion,
                                  JsonNode document) throws IOException, InterruptedException {
        var body = mapper.createObjectNode();
        body.set("document", document);
        body.put("version", expectedVersion + 1);
        var response = transport.send("PATCH",
                rowUrl(settings, auth) + "&version=eq." + expectedVersion,
                writeHeaders(settings, auth),
                body.toString());
        return rows(response, "Push");
    }

    private JsonNode insert(CloudSyncSettings settings, Auth auth, JsonNode document)
            throws IOException, InterruptedException {
        var body = mapper.createObjectNode();
        body.put("owner_user_id", auth.userId());
        body.put("name", WORKSPACE_NAME);
        body.put("version", 1);
        body.set("document", document);
        var response = transport.send("POST",
                settings.getSupabaseUrl() + "/rest/v1/workspaces",
                writeHeaders(settings, auth),
                body.toString());
        var rows = rows(response, "First push");
        if (rows.size() != 1) throw new SyncFailure("First push: expected 1 created row, got " + rows.size());
        return rows.get(0);
    }

    private JsonNode fetchRow(CloudSyncSettings settings, Auth auth) throws IOException, InterruptedException {
        var response = transport.send("GET",
                rowUrl(settings, auth) + "&select=*",
                Map.of("apikey", settings.getPublishableKey(),
                       "Authorization", "Bearer " + auth.token()),
                null);
        var rows = rows(response, "Fetch");
        return rows.isEmpty() ? null : rows.get(0);
    }

    private PushResult pushed(CloudSyncSettings settings, JsonNode row) {
        var version = row.path("version").asLong();
        settings.setLastSyncedVersion(version);
        return PushResult.ok(version);
    }

    private String rowUrl(CloudSyncSettings settings, Auth auth) {
        return settings.getSupabaseUrl() + "/rest/v1/workspaces?owner_user_id=eq." + auth.userId()
                + "&name=eq." + WORKSPACE_NAME;
    }

    private Map<String, String> writeHeaders(CloudSyncSettings settings, Auth auth) {
        return Map.of("apikey", settings.getPublishableKey(),
                      "Authorization", "Bearer " + auth.token(),
                      "Content-Type", "application/json",
                      "Prefer", "return=representation");
    }

    private JsonNode rows(Response response, String step) throws IOException {
        if (response.status() >= 300) {
            throw new SyncFailure(step + " failed (HTTP " + response.status() + ")");
        }
        var json = mapper.readTree(response.body());
        if (!json.isArray()) throw new SyncFailure(step + ": unexpected response shape");
        return json;
    }

    private static final class SyncFailure extends IOException {
        SyncFailure(String message) { super(message); }
    }

    private static Transport httpClientTransport() {
        var client = HttpClient.newHttpClient();
        return (method, url, headers, body) -> {
            var builder = HttpRequest.newBuilder().uri(URI.create(url));
            headers.forEach(builder::header);
            builder.method(method, body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        };
    }
}
