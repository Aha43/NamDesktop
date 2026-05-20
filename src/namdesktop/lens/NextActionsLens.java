package namdesktop.lens;

import namdesktop.model.NamWorkspace;

import java.util.List;

public final class NextActionsLens {

    public List<NextActionItemRow> items(NamWorkspace workspace) {
        var nextActionsId = workspace.getNextActionsNodeId();
        if (nextActionsId == null) return List.of();
        return workspace.getChildren(nextActionsId).stream()
                .map(n -> new NextActionItemRow(n.getId(), n.getTitle(), n.getStatus()))
                .toList();
    }
}
