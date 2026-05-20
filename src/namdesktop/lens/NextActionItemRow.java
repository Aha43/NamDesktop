package namdesktop.lens;

import namdesktop.model.NodeStatus;

import java.util.UUID;

public record NextActionItemRow(UUID id, String title, NodeStatus status) {}
