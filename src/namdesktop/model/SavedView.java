package namdesktop.model;

import java.util.List;

public record SavedView(String name, List<String> tags, boolean nextOnly) {
    public SavedView(String name, List<String> tags) { this(name, tags, false); }
}
