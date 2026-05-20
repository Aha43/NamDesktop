package namdesktop.lens;

import namdesktop.model.NodeStatus;

import java.util.UUID;

public record InboxItemRow(UUID id, String title, NodeStatus status) {}
