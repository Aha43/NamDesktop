package namdesktop.model;

public final class Resource {

    private ResourceType type;
    private String value;
    private String description;

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
