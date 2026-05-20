package namdesktop.lens;

import namdesktop.model.NodeStatus;

import java.util.UUID;

public record SearchResultRow(UUID id, String title, String type, String parentTitle, NodeStatus status) {}
