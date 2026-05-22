package namdesktop.lens;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ProjectWorkbenchLens {

    public WorkbenchProjection project(NamWorkspace workspace, UUID projectId) {
        return new WorkbenchProjection(
                breadcrumb(workspace, projectId),
                directActions(workspace, projectId),
                childSections(workspace, projectId));
    }

    private List<NamNode> breadcrumb(NamWorkspace workspace, UUID projectId) {
        var path = new ArrayList<NamNode>();
        var current = workspace.getNode(projectId);
        var projectsNodeId = workspace.getProjectsNodeId();
        while (current.isPresent() && !current.get().getId().equals(projectsNodeId)) {
            path.add(0, current.get());
            current = workspace.getParent(current.get().getId());
        }
        return List.copyOf(path);
    }

    private List<NamNode> directActions(NamWorkspace workspace, UUID projectId) {
        return workspace.getChildren(projectId).stream()
                .filter(n -> !n.isProject())
                .toList();
    }

    private List<ChildSection> childSections(NamWorkspace workspace, UUID projectId) {
        return workspace.getChildren(projectId).stream()
                .filter(NamNode::isProject)
                .map(child -> new ChildSection(child, directActions(workspace, child.getId())))
                .toList();
    }
}
