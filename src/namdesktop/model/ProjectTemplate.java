package namdesktop.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ProjectTemplate(
        @JsonProperty("name")     String name,
        @JsonProperty("children") List<TemplateNode> children) {}
