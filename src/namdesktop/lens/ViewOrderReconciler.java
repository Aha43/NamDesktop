package namdesktop.lens;

import namdesktop.model.NamNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ViewOrderReconciler {

    /**
     * Returns a reconciled list: saved-order items that are still live come first (in saved order),
     * then any live items not yet in the saved order are appended (in their original live order).
     */
    public List<NamNode> reconcile(List<UUID> savedOrder, List<NamNode> liveItems) {
        var liveById   = liveItems.stream().collect(Collectors.toMap(NamNode::getId, n -> n));
        var savedSet   = new HashSet<>(savedOrder);
        var result     = new ArrayList<NamNode>();

        for (var id : savedOrder) {
            var node = liveById.get(id);
            if (node != null) result.add(node);
        }
        for (var node : liveItems) {
            if (!savedSet.contains(node.getId())) result.add(node);
        }
        return result;
    }
}
