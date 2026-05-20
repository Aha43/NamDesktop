package namdesktop.lens;

import namdesktop.model.NodeStatus;

import java.util.UUID;

public record ProjectItemRow(UUID id, String title, NodeStatus status) {}
