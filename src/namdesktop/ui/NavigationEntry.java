package namdesktop.ui;

public record NavigationEntry(String id, String title) {
    public static NavigationEntry divider() { return new NavigationEntry("__divider__", ""); }
    public boolean isDivider() { return "__divider__".equals(id); }
}
