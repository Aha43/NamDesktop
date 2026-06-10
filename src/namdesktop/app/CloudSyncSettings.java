package namdesktop.app;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Cloud sync configuration (#215, #350). Defaults point at the local Supabase stack
 * (see docs/features/supabase-poc/setup.md) so dev sync works with zero config.
 * The password is stored plain in the local settings JSON — accepted trade-off
 * for a personal tool; it is only ever sent to the configured Supabase URL.
 *
 * Each remote workspace row (named per {@code WORKSPACE_*}) has its own version
 * watermark — a dev-mode sync must never move the default workspace's watermark.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CloudSyncSettings {

    public static final String DEFAULT_SUPABASE_URL    = "http://127.0.0.1:54321";
    public static final String DEFAULT_PUBLISHABLE_KEY = "sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH";

    /** Remote row names — one workspace row per (user, name). */
    public static final String WORKSPACE_DEFAULT = "default";
    public static final String WORKSPACE_DEV     = "dev";

    private boolean enabled              = false;
    private String  supabaseUrl          = DEFAULT_SUPABASE_URL;
    private String  publishableKey       = DEFAULT_PUBLISHABLE_KEY;
    private String  email                = "";
    private String  password             = "";
    private long    lastSyncedVersion    = 0;
    private long    lastSyncedVersionDev = 0;

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
    public long    getLastSyncedVersionDev()       { return lastSyncedVersionDev; }
    public void    setLastSyncedVersionDev(long v) { this.lastSyncedVersionDev = Math.max(0, v); }

    /** Watermark for the given remote workspace name. */
    public long lastSyncedVersionFor(String workspaceName) {
        return WORKSPACE_DEV.equals(workspaceName) ? lastSyncedVersionDev : lastSyncedVersion;
    }

    /** Set the watermark for the given remote workspace name only. */
    public void setLastSyncedVersionFor(String workspaceName, long v) {
        if (WORKSPACE_DEV.equals(workspaceName)) setLastSyncedVersionDev(v);
        else                                     setLastSyncedVersion(v);
    }
}
