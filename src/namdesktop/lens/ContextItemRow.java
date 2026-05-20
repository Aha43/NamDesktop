package namdesktop.lens;

import namdesktop.model.NodeStatus;

import java.util.List;
import java.util.UUID;

public record ContextItemRow(UUID id, String title, NodeStatus status, String parentTitle, List<String> tags) {}
