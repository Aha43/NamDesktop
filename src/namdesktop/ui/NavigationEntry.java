package namdesktop.ui;

public record NavigationEntry(String id, String title, String tooltip) {
    public NavigationEntry(String id, String title) { this(id, title, null); }
    public static NavigationEntry divider() { return new NavigationEntry("__divider__", "", null); }
    public boolean isDivider() { return "__divider__".equals(id); }
}
