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

    private static AppSettings instance;

    public static AppSettings getInstance() { return instance; }
    public static void setInstance(AppSettings s) { instance = s; }

    private Theme   theme            = Theme.DARK;
    private boolean dense            = false;
    private String  syncRepoUrl      = "";
    private boolean showStatusColumn = false;
    private boolean startMaximized   = false;
    private boolean showToolbar      = true;
    private boolean showNavPane      = true;
    private String  lastNavId        = null;
    private String  lastProjectId    = null;
    private boolean welcomed         = false;
    private boolean backlogFreeOnly  = true;
    private boolean powerMode        = false;

    public Theme   getTheme()                        { return theme; }
    public void    setTheme(Theme theme)             { this.theme = theme != null ? theme : Theme.DARK; }
    public boolean isDense()                         { return dense; }
    public void    setDense(boolean dense)           { this.dense = dense; }
    public String  getSyncRepoUrl()                  { return syncRepoUrl != null ? syncRepoUrl : ""; }
    public void    setSyncRepoUrl(String syncRepoUrl){ this.syncRepoUrl = syncRepoUrl != null ? syncRepoUrl.strip() : ""; }
    public boolean isShowStatusColumn()              { return showStatusColumn; }
    public void    setShowStatusColumn(boolean v)    { this.showStatusColumn = v; }
    public boolean isStartMaximized()                { return startMaximized; }
    public void    setStartMaximized(boolean v)      { this.startMaximized = v; }
    public boolean isShowToolbar()                   { return showToolbar; }
    public void    setShowToolbar(boolean v)         { this.showToolbar = v; }
    public boolean isShowNavPane()                   { return showNavPane; }
    public void    setShowNavPane(boolean v)         { this.showNavPane = v; }
    public String  getLastNavId()                    { return lastNavId; }
    public void    setLastNavId(String v)            { this.lastNavId = v; }
    public String  getLastProjectId()                { return lastProjectId; }
    public void    setLastProjectId(String v)        { this.lastProjectId = v; }
    public boolean isWelcomed()                      { return welcomed; }
    public void    setWelcomed(boolean v)            { this.welcomed = v; }
    public boolean isBacklogFreeOnly()               { return backlogFreeOnly; }
    public void    setBacklogFreeOnly(boolean v)     { this.backlogFreeOnly = v; }
    public boolean isPowerMode()                     { return powerMode; }
    public void    setPowerMode(boolean v)           { this.powerMode = v; }

    public static AppSettings load() {
        try {
            if (Files.exists(SETTINGS_PATH)) {
                var dto = MAPPER.readValue(SETTINGS_PATH.toFile(), SettingsFile.class);
                var s = new AppSettings();
                s.setTheme(dto.theme);
                s.setDense(dto.dense != null && dto.dense);
                s.setSyncRepoUrl(dto.syncRepoUrl);
                s.setShowStatusColumn(dto.showStatusColumn != null && dto.showStatusColumn);
                s.setStartMaximized(dto.startMaximized != null && dto.startMaximized);
                s.setShowToolbar(dto.showToolbar == null || dto.showToolbar);
                s.setShowNavPane(dto.showNavPane == null || dto.showNavPane);
                s.setLastNavId(dto.lastNavId);
                s.setLastProjectId(dto.lastProjectId);
                s.setWelcomed(dto.welcomed != null && dto.welcomed);
                s.setBacklogFreeOnly(dto.backlogFreeOnly == null || dto.backlogFreeOnly);
                s.setPowerMode(dto.powerMode != null && dto.powerMode);
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
        dto.theme            = this.theme;
        dto.dense            = this.dense;
        dto.syncRepoUrl      = this.syncRepoUrl;
        dto.showStatusColumn = this.showStatusColumn;
        dto.startMaximized   = this.startMaximized;
        dto.showToolbar      = this.showToolbar;
        dto.showNavPane      = this.showNavPane;
        dto.lastNavId        = this.lastNavId;
        dto.lastProjectId    = this.lastProjectId;
        dto.welcomed         = this.welcomed;
        dto.backlogFreeOnly  = this.backlogFreeOnly;
        dto.powerMode        = this.powerMode;
        MAPPER.writeValue(SETTINGS_PATH.toFile(), dto);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class SettingsFile {
        public Theme   theme;
        public Boolean dense;
        public String  syncRepoUrl;
        public Boolean showStatusColumn;
        public Boolean startMaximized;
        public Boolean showToolbar;
        public Boolean showNavPane;
        public String  lastNavId;
        public String  lastProjectId;
        public Boolean welcomed;
        public Boolean backlogFreeOnly;
        public Boolean powerMode;
    }
}
