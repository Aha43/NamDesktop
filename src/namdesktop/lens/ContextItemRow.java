package namdesktop.lens;

import namdesktop.model.NodeStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ContextItemRow(UUID id, String title, NodeStatus status, String parentTitle,
                             String projectPath, List<String> tags, List<String> inheritedTags,
                             boolean hasResources, LocalDate dueAt) {}
