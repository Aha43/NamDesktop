package namdesktop.lens;

import namdesktop.model.NodeStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record NextActionItemRow(UUID id, String title, NodeStatus status,
                                String parentTitle, UUID parentId, boolean isSubProject,
                                String projectPath, List<String> tags, List<String> inheritedTags,
                                boolean hasResources, LocalDateTime updatedAt, LocalDateTime createdAt) {}
