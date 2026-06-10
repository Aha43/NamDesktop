package namdesktop.spike;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

/**
 * Exploratory spike for the Supabase PoC (#349) — not wired into the app or test suite.
 *
 * Signs in to Supabase (local stack from #348 or hosted), inserts a workspace document,
 * pulls it back, updates it with optimistic versioning, and verifies that a stale-version
 * update is detectable as a conflict. PostgREST signals a version mismatch with 200 and
 * zero rows updated (never 409), so conflict detection counts returned rows via
 * {@code Prefer: return=representation}.
 *
 * Run with: make spike (env: SUPABASE_URL, SUPABASE_ANON_KEY, SUPABASE_TEST_EMAIL,
 * SUPABASE_TEST_PASSWORD — see docs/features/supabase-poc/setup.md).
 */
public final class SupabaseSpike {

    private final String baseUrl;
    private final String anonKey;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private String accessToken;
    private String userId;

    private SupabaseSpike(String baseUrl, String anonKey) {
        this.baseUrl = baseUrl;
        this.anonKey = anonKey;
    }

    public static void main(String[] args) throws Exception {
        var spike = new SupabaseSpike(env("SUPABASE_URL"), env("SUPABASE_ANON_KEY"));
        spike.signIn(env("SUPABASE_TEST_EMAIL"), env("SUPABASE_TEST_PASSWORD"));
        var workspaceId = spike.insertWorkspace();
        try {
            spike.pullAndVerify(workspaceId);
            spike.updateWithCorrectVersion(workspaceId);
            spike.simulateConflict(workspaceId);
        } finally {
            spike.delete(workspaceId);
        }
        System.out.println("ALL STEPS PASSED");
    }

    private void signIn(String email, String password) throws Exception {
        var body = mapper.createObjectNode();
        body.put("email", email);
        body.put("password", password);
        var response = send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/v1/token?grant_type=password"))
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body.toString()))
                .build());
        var json = expectOk(response, "sign in");
        accessToken = json.path("access_token").asText();
        userId = json.path("user").path("id").asText();
        if (accessToken.isEmpty() || userId.isEmpty()) {
            fail("sign in: no access_token or user.id in response: " + json);
        }
        System.out.println("[1/6] sign in            ok (user " + userId + ")");
    }

    private String insertWorkspace() throws Exception {
        var body = mapper.createObjectNode();
        body.put("owner_user_id", userId);
        body.put("name", "default");
        body.put("version", 1);
        body.set("document", document("NAM Spike"));
        var response = send(rest("/rest/v1/workspaces")
                .header("Prefer", "return=representation")
                .POST(BodyPublishers.ofString(body.toString()))
                .build());
        var rows = expectOk(response, "insert workspace");
        if (rows.size() != 1) fail("insert workspace: expected 1 returned row, got " + rows);
        var id = rows.get(0).path("id").asText();
        System.out.println("[2/6] insert workspace   ok (id " + id + ")");
        return id;
    }

    private void pullAndVerify(String id) throws Exception {
        var response = send(rest("/rest/v1/workspaces?id=eq." + id + "&select=*").GET().build());
        var rows = expectOk(response, "pull workspace");
        var title = rows.path(0).path("document").path("title").asText();
        if (!"NAM Spike".equals(title)) {
            fail("pull workspace: expected document.title \"NAM Spike\", got \"" + title + "\"");
        }
        System.out.println("[3/6] pull and verify    ok (document.title roundtripped)");
    }

    private void updateWithCorrectVersion(String id) throws Exception {
        var updated = patchWithVersion(id, 1, document("NAM Spike v2"), 2);
        if (updated != 1) fail("versioned update: expected 1 row updated, got " + updated);
        System.out.println("[4/6] versioned update   ok (version 1 -> 2)");
    }

    private void simulateConflict(String id) throws Exception {
        var updated = patchWithVersion(id, 1, document("stale write"), 3);
        if (updated != 0) fail("stale update: expected 0 rows updated, got " + updated);
        System.out.println("[5/6] CONFLICT DETECTED (expected) — stale version updated 0 rows");
    }

    /** Optimistic update: PATCH guarded by version=eq.<expected>; returns rows updated. */
    private int patchWithVersion(String id, int expectedVersion, ObjectNode document, int newVersion)
            throws Exception {
        var body = mapper.createObjectNode();
        body.set("document", document);
        body.put("version", newVersion);
        var response = send(rest("/rest/v1/workspaces?id=eq." + id + "&version=eq." + expectedVersion)
                .header("Prefer", "return=representation")
                .method("PATCH", BodyPublishers.ofString(body.toString()))
                .build());
        return expectOk(response, "patch workspace").size();
    }

    private void delete(String id) throws Exception {
        var response = send(rest("/rest/v1/workspaces?id=eq." + id).DELETE().build());
        if (response.statusCode() >= 300) {
            fail("delete workspace: HTTP " + response.statusCode() + " " + response.body());
        }
        System.out.println("[6/6] cleanup            ok (test row deleted)");
    }

    private ObjectNode document(String title) {
        var document = mapper.createObjectNode();
        document.put("title", title);
        document.putArray("nodes");
        return document;
    }

    /** Request builder for PostgREST calls — anon key plus the signed-in user's JWT. */
    private HttpRequest.Builder rest(String pathAndQuery) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + pathAndQuery))
                .header("apikey", anonKey)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json");
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, BodyHandlers.ofString());
    }

    private JsonNode expectOk(HttpResponse<String> response, String step) throws Exception {
        if (response.statusCode() >= 300) {
            fail(step + ": HTTP " + response.statusCode() + " " + response.body());
        }
        return mapper.readTree(response.body());
    }

    private static void fail(String message) {
        System.err.println("SPIKE FAILED — " + message);
        System.exit(1);
    }

    private static String env(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            System.err.println("Missing env var " + name + " — see docs/features/supabase-poc/setup.md");
            System.exit(1);
        }
        return value;
    }
}
