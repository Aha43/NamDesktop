package namdesktop.app;

import java.io.InputStream;

public final class AppInfo {

    public static final String NAME = "NamDesktop";

    private AppInfo() {}

    public static String version() {
        try (InputStream stream = AppInfo.class.getResourceAsStream("/VERSION")) {
            if (stream == null) return "unknown";
            return new String(stream.readAllBytes()).trim();
        } catch (Exception e) {
            return "unknown";
        }
    }
}