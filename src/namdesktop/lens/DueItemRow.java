package namdesktop.lens;

import namdesktop.model.NodeStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DueItemRow(UUID id, String title, NodeStatus status,
                         String projectPath, UUID parentId,
                         List<String> tags, List<String> inheritedTags,
                         LocalDate dueAt) {}
