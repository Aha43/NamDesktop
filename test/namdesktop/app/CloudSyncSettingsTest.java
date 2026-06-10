package namdesktop.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CloudSyncSettingsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void defaultsPointAtLocalStack() {
        var s = new CloudSyncSettings();
        assertFalse(s.isEnabled());
        assertEquals("http://127.0.0.1:54321", s.getSupabaseUrl());
        assertEquals(CloudSyncSettings.DEFAULT_PUBLISHABLE_KEY, s.getPublishableKey());
        assertEquals("", s.getEmail());
        assertEquals("", s.getPassword());
        assertEquals(0, s.getLastSyncedVersion());
    }

    @Test
    void blankUrlAndKeyFallBackToDefaults() {
        var s = new CloudSyncSettings();
        s.setSupabaseUrl("  ");
        s.setPublishableKey("");
        assertEquals(CloudSyncSettings.DEFAULT_SUPABASE_URL, s.getSupabaseUrl());
        assertEquals(CloudSyncSettings.DEFAULT_PUBLISHABLE_KEY, s.getPublishableKey());
    }

    @Test
    void nullSettersAreSafe() {
        var s = new CloudSyncSettings();
        s.setSupabaseUrl(null);
        s.setPublishableKey(null);
        s.setEmail(null);
        s.setPassword(null);
        assertEquals(CloudSyncSettings.DEFAULT_SUPABASE_URL, s.getSupabaseUrl());
        assertEquals(CloudSyncSettings.DEFAULT_PUBLISHABLE_KEY, s.getPublishableKey());
        assertEquals("", s.getEmail());
        assertEquals("", s.getPassword());
    }

    @Test
    void emailAndUrlAreStripped() {
        var s = new CloudSyncSettings();
        s.setEmail("  user@example.com  ");
        s.setSupabaseUrl(" https://xyz.supabase.co ");
        assertEquals("user@example.com", s.getEmail());
        assertEquals("https://xyz.supabase.co", s.getSupabaseUrl());
    }

    @Test
    void lastSyncedVersionNeverNegative() {
        var s = new CloudSyncSettings();
        s.setLastSyncedVersion(-5);
        assertEquals(0, s.getLastSyncedVersion());
        s.setLastSyncedVersion(7);
        assertEquals(7, s.getLastSyncedVersion());
    }

    @Test
    void jsonRoundtripPreservesAllFields() throws Exception {
        var s = new CloudSyncSettings();
        s.setEnabled(true);
        s.setSupabaseUrl("https://xyz.supabase.co");
        s.setPublishableKey("sb_publishable_other");
        s.setEmail("user@example.com");
        s.setPassword("secret pass");
        s.setLastSyncedVersion(42);

        var back = mapper.readValue(mapper.writeValueAsString(s), CloudSyncSettings.class);
        assertTrue(back.isEnabled());
        assertEquals("https://xyz.supabase.co", back.getSupabaseUrl());
        assertEquals("sb_publishable_other", back.getPublishableKey());
        assertEquals("user@example.com", back.getEmail());
        assertEquals("secret pass", back.getPassword());
        assertEquals(42, back.getLastSyncedVersion());
    }

    @Test
    void unknownJsonPropertiesAreIgnored() throws Exception {
        var back = mapper.readValue("{\"enabled\":true,\"futureField\":\"x\"}", CloudSyncSettings.class);
        assertTrue(back.isEnabled());
        assertEquals(CloudSyncSettings.DEFAULT_SUPABASE_URL, back.getSupabaseUrl());
    }
}
