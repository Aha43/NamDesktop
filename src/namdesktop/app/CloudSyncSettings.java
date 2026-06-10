package namdesktop.app;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Cloud sync configuration (#215). Defaults point at the local Supabase stack
 * (see docs/features/supabase-poc/setup.md) so dev sync works with zero config.
 * The password is stored plain in the local settings JSON — accepted trade-off
 * for a personal tool; it is only ever sent to the configured Supabase URL.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CloudSyncSettings {

    public static final String DEFAULT_SUPABASE_URL    = "http://127.0.0.1:54321";
    public static final String DEFAULT_PUBLISHABLE_KEY = "sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH";

    private boolean enabled           = false;
    private String  supabaseUrl       = DEFAULT_SUPABASE_URL;
    private String  publishableKey    = DEFAULT_PUBLISHABLE_KEY;
    private String  email             = "";
    private String  password          = "";
    private long    lastSyncedVersion = 0;

    public boolean isEnabled()                  { return enabled; }
    public void    setEnabled(boolean v)        { this.enabled = v; }
    public String  getSupabaseUrl()             { return supabaseUrl != null && !supabaseUrl.isBlank() ? supabaseUrl : DEFAULT_SUPABASE_URL; }
    public void    setSupabaseUrl(String v)     { this.supabaseUrl = v != null ? v.strip() : DEFAULT_SUPABASE_URL; }
    public String  getPublishableKey()          { return publishableKey != null && !publishableKey.isBlank() ? publishableKey : DEFAULT_PUBLISHABLE_KEY; }
    public void    setPublishableKey(String v)  { this.publishableKey = v != null ? v.strip() : DEFAULT_PUBLISHABLE_KEY; }
    public String  getEmail()                   { return email != null ? email : ""; }
    public void    setEmail(String v)           { this.email = v != null ? v.strip() : ""; }
    public String  getPassword()                { return password != null ? password : ""; }
    public void    setPassword(String v)        { this.password = v != null ? v : ""; }
    public long    getLastSyncedVersion()       { return lastSyncedVersion; }
    public void    setLastSyncedVersion(long v) { this.lastSyncedVersion = Math.max(0, v); }
}
