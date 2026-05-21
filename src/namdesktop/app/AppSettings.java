package namdesktop.app;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AppSettings {

    private static final Path SETTINGS_PATH = Path.of(
            System.getProperty("user.home"), ".namdesktop", "settings.json");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private Theme theme;

    public AppSettings() { this.theme = Theme.DARK; }

    public Theme getTheme() { return theme; }
    public void setTheme(Theme theme) { this.theme = theme != null ? theme : Theme.DARK; }

    public static AppSettings load() {
        try {
            if (Files.exists(SETTINGS_PATH)) {
                var dto = MAPPER.readValue(SETTINGS_PATH.toFile(), SettingsFile.class);
                var s = new AppSettings();
                s.setTheme(dto.theme);
                return s;
            }
        } catch (Exception e) {
            System.err.println("Failed to load settings, using defaults: " + e.getMessage());
        }
        return new AppSettings();
    }

    public void save() throws IOException {
        Files.createDirectories(SETTINGS_PATH.getParent());
        var dto = new SettingsFile();
        dto.theme = this.theme;
        MAPPER.writeValue(SETTINGS_PATH.toFile(), dto);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class SettingsFile {
        public Theme theme;
    }
}
