package namdesktop.sync;

import namdesktop.model.NamWorkspace;

/**
 * Outcome of a cloud pull. Success carries the deserialized remote workspace and its
 * version. {@code nothingToPull} means no remote row exists yet — first-push state,
 * not an error.
 */
public record PullResult(boolean success, NamWorkspace workspace, long remoteVersion,
                         boolean nothingToPull, String error) {

    public static PullResult ok(NamWorkspace workspace, long remoteVersion) {
        return new PullResult(true, workspace, remoteVersion, false, null);
    }
    public static PullResult noRemote() { return new PullResult(false, null, 0, true, null); }
    public static PullResult failure(String error) { return new PullResult(false, null, 0, false, error); }
}
