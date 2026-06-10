package namdesktop.sync;

/**
 * Outcome of a cloud push. Exactly one of three shapes: success (with the new remote
 * version), conflict (remote moved past the local watermark; carries the remote version
 * so "Keep local" can re-push against it), or failure (with a user-presentable reason).
 */
public record PushResult(boolean success, long remoteVersion, boolean conflict, String error) {

    public static PushResult ok(long remoteVersion)       { return new PushResult(true, remoteVersion, false, null); }
    public static PushResult conflict(long remoteVersion) { return new PushResult(false, remoteVersion, true, null); }
    public static PushResult failure(String error)        { return new PushResult(false, 0, false, error); }
}
