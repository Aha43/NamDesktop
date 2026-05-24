package namdesktop.lens;

import namdesktop.model.NodeStatus;

import java.util.List;
import java.util.UUID;

public record BacklogItemRow(UUID id, String title, NodeStatus status,
                             String parentTitle, UUID parentId, boolean isSubProject,
                             String projectPath, List<String> tags, boolean isInboxItem) {}
