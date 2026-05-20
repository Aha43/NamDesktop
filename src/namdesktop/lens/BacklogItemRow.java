package namdesktop.lens;

import namdesktop.model.NodeStatus;

import java.util.UUID;

public record BacklogItemRow(UUID id, String title, NodeStatus status, String parentTitle) {}
