package swingdemo;

/**
 * Signals the host application to refresh its UI after a script step executes.
 * Wire this to whatever "repaint all panels" means for a given app.
 */
public interface RefreshBus {
    void refresh();
}
