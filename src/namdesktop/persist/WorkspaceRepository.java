package namdesktop.persist;

import namdesktop.model.NamWorkspace;

import java.io.IOException;
import java.nio.file.Path;

public interface WorkspaceRepository {
    NamWorkspace load(Path path) throws IOException;
    void save(Path path, NamWorkspace workspace) throws IOException;
}
