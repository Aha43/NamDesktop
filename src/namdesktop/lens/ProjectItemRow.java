package namdesktop.lens;

import namdesktop.model.NodeStatus;

import java.util.List;
import java.util.UUID;

public record ProjectItemRow(UUID id, String title, NodeStatus status, List<String> tags, boolean hasResources) {}
