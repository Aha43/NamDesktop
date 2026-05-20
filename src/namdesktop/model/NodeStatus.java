package namdesktop.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public enum NodeStatus {
    @JsonAlias("ACTIVE") NEXT,
    BACKLOG,
    DONE,
    CANCELLED,
    ARCHIVED
}
