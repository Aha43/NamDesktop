package namdesktop.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Resource {

    private ResourceType type;
    private String value;
    private String description;
    private final Map<String, Object> unknownFields = new LinkedHashMap<>();  // preserve foreign fields (#416)

    public Resource() {}

    public Resource(ResourceType type, String value, String description) {
        this.type        = type;
        this.value       = value;
        this.description = description;
    }

    public ResourceType getType()        { return type; }
    public void setType(ResourceType t)  { this.type = t; }

    public String getValue()             { return value; }
    public void setValue(String v)       { this.value = v; }

    public String getDescription()       { return description; }
    public void setDescription(String d) { this.description = d; }

    @JsonAnyGetter
    public Map<String, Object> unknownFields() { return unknownFields; }

    @JsonAnySetter
    public void putUnknownField(String name, Object value) { unknownFields.put(name, value); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Resource r)) return false;
        return type == r.type
                && java.util.Objects.equals(value, r.value)
                && java.util.Objects.equals(description, r.description);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(type, value, description);
    }
}
