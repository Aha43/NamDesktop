package namdesktop.ui;

import java.util.UUID;

/** Value type capturing where the user is in the app. Used by NavigationHistory. */
public sealed interface NavigationLocation {
    record PanelLocation(String panelId)   implements NavigationLocation {}
    record WorkbenchLocation(UUID projectId) implements NavigationLocation {}
    record SavedViewLocation(String viewName) implements NavigationLocation {}
}
