package namdesktop.lens;

import namdesktop.model.NamWorkspace;

import java.util.List;

public final class ProjectsLens {

    public List<ProjectItemRow> items(NamWorkspace workspace) {
        var projectsId = workspace.getProjectsNodeId();
        if (projectsId == null) return List.of();
        return workspace.getChildren(projectsId).stream()
                .map(n -> new ProjectItemRow(n.getId(), n.getTitle(), n.getStatus(), List.copyOf(n.getTags())))
                .toList();
    }
}
