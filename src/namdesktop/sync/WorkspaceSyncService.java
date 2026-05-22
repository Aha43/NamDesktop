package namdesktop.sync;

import java.io.IOException;
import java.nio.file.Path;

public interface WorkspaceSyncService {
    void push(Path workspacePath) throws IOException;
    void pull(Path workspacePath) throws IOException;
    boolean isConfigured();
}
