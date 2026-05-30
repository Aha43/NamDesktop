package namdesktop.lens;

import namdesktop.model.NamWorkspace;

import java.util.List;

public final class InboxLens {

    public List<InboxItemRow> items(NamWorkspace workspace) {
        return workspace.getInboxItems().stream()
                .map(n -> new InboxItemRow(n.getId(), n.getTitle(), n.getStatus(), !n.getResources().isEmpty()))
                .toList();
    }
}
