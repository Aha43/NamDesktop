package namdesktop.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class NamNode {

    private UUID id;
    private String title;
    private String description;
    private NodeStatus status = NodeStatus.BACKLOG;
    private List<UUID> childIds = new ArrayList<>();
    private List<String> tags = new ArrayList<>();

    public NamNode() {}

    public NamNode(UUID id, String title) {
        this.id = id;
        this.title = title;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public NodeStatus getStatus() { return status; }
    public void setStatus(NodeStatus status) { this.status = status; }

    public List<UUID> getChildIds() { return childIds; }
    public void setChildIds(List<UUID> childIds) { this.childIds = childIds; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags != null ? tags : new ArrayList<>(); }

    @Override
    public String toString() { return title; }
}
