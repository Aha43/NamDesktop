package namdesktop.sync;

import namdesktop.app.CloudSyncSettings;
import namdesktop.model.NamWorkspace;

/**
 * Cloud sync engine seam (#216). The v1 implementation talks to Supabase directly
 * ({@link SupabaseSyncService}); a future web-API implementation can swap in behind
 * this interface without touching desktop code.
 *
 * Implementations update {@code settings.lastSyncedVersion} in memory after a
 * successful push or pull; persisting the settings is the caller's responsibility.
 */
public interface CloudSyncService {

    PushResult push(NamWorkspace workspace, CloudSyncSettings settings);

    PullResult pull(CloudSyncSettings settings);
}
