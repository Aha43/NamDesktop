package namdesktop.lens;

import namdesktop.model.NamWorkspace;

import java.util.List;
import java.util.Set;

public final class ProjectsLens {

    public List<ProjectItemRow> items(NamWorkspace workspace) {
        return items(workspace, List.of());
    }

    public List<ProjectItemRow> items(NamWorkspace workspace, List<String> filterTags) {
        var projectsId = workspace.getProjectsNodeId();
        if (projectsId == null) return List.of();
        var tagSet = filterTags.isEmpty() ? null : Set.copyOf(filterTags);
        return workspace.getChildren(projectsId).stream()
                .filter(n -> tagSet == null || n.getTags().stream().anyMatch(tagSet::contains))
                .map(n -> new ProjectItemRow(n.getId(), n.getTitle(), n.getStatus(), List.copyOf(n.getTags()), !n.getResources().isEmpty()))
                .toList();
    }
}
