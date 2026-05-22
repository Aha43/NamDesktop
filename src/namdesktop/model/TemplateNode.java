package namdesktop.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TemplateNode(
        @JsonProperty("title")    String title,
        @JsonProperty("children") List<TemplateNode> children) {}
