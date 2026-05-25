package namdesktop.lens;

import namdesktop.model.MissionControl;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;

import java.util.*;
import java.util.stream.Collectors;

public final class MissionControlLens {

    public List<MissionControlStation> stations(MissionControl mc, NamWorkspace workspace) {
        var tags = Set.copyOf(mc.tags());

        // All project nodes that explicitly carry any mission tag (own tags, not inherited)
        var tagged = workspace.getNodes().values().stream()
                .filter(n -> n.isProject() && n.getTags().stream().anyMatch(tags::contains))
                .map(n -> n.getId())
                .collect(Collectors.toSet());

        var result = new ArrayList<MissionControlStation>();
        for (var id : tagged) {
            // Discard any node whose ancestor is also tagged — keep only the highest
            if (hasTaggedAncestor(id, tagged, workspace)) continue;

            var node    = workspace.getNode(id).orElseThrow();
            var subtree = workspace.collectSubtree(id);

            int subProjectCount = 0, doneCount = 0, totalActions = 0, rolledUp = 0;
            for (var subId : subtree) {
                if (subId.equals(id)) continue;
                var sub = workspace.getNode(subId);
                if (sub.isEmpty()) continue;
                var subNode = sub.get();
                if (subNode.isProject()) {
                    subProjectCount++;
                    if (tagged.contains(subId)) rolledUp++;
                } else {
                    totalActions++;
                    if (subNode.getStatus() == NodeStatus.DONE) doneCount++;
                }
            }

            result.add(new MissionControlStation(
                    id, node.getTitle(),
                    subProjectCount, computeMaxDepth(id, workspace),
                    doneCount, totalActions, rolledUp));
        }

        result.sort(Comparator.comparing(MissionControlStation::title));
        return List.copyOf(result);
    }

    private boolean hasTaggedAncestor(UUID nodeId, Set<UUID> tagged, NamWorkspace workspace) {
        var ancestor = workspace.getParent(nodeId);
        while (ancestor.isPresent()) {
            if (tagged.contains(ancestor.get().getId())) return true;
            ancestor = workspace.getParent(ancestor.get().getId());
        }
        return false;
    }

    private int computeMaxDepth(UUID nodeId, NamWorkspace workspace) {
        return computeDepth(nodeId, workspace, 0);
    }

    private int computeDepth(UUID nodeId, NamWorkspace workspace, int depth) {
        var children = workspace.getChildren(nodeId);
        if (children.isEmpty()) return depth;
        return children.stream()
                .mapToInt(child -> computeDepth(child.getId(), workspace, depth + 1))
                .max()
                .orElse(depth);
    }
}
