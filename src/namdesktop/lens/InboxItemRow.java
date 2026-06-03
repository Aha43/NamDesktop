package namdesktop.lens;

import namdesktop.model.NodeStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record InboxItemRow(UUID id, String title, NodeStatus status, boolean hasResources,
                           LocalDateTime updatedAt, LocalDateTime createdAt) {}
