package namdesktop.ui;

import namdesktop.service.MonitoringMode;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

/**
 * Guards mutating UI actions when monitoring mode is active.
 *
 * Call MonitoringModeGuard.configure(...) once from MainFrame after construction.
 * Call checkAndConfirm(parent) at the point of initiation for every mutating action.
 * Returns true (proceed) or false (cancel).
 *
 * Once the user chooses "Continue anyway" the dialog is suppressed for the rest of
 * the session. Resets on app restart.
 */
public final class MonitoringModeGuard {

    private static Path     workspacePath;
    private static Runnable exitCallback       = () -> {};
    private static boolean  sessionAcknowledged = false;

    private MonitoringModeGuard() {}

    public static void configure(Path wsPath, Runnable onExitMonitoringMode) {
        workspacePath       = wsPath;
        exitCallback        = onExitMonitoringMode != null ? onExitMonitoringMode : () -> {};
        sessionAcknowledged = false;
    }

    /**
     * Returns true immediately when:
     * - monitoring mode is not active, or
     * - the user already chose "Continue anyway" this session.
     *
     * Otherwise shows a modal dialog offering Exit / Continue / Cancel.
     */
    public static boolean checkAndConfirm(Component parent) {
        if (workspacePath == null || !MonitoringMode.isActive(workspacePath)) return true;
        if (sessionAcknowledged) return true;

        var exitBtn     = new JButton("Exit monitoring mode");
        var continueBtn = new JButton("Continue (don't ask again this session)");
        var cancelBtn   = new JButton("Cancel this action");

        var message = new JLabel(
                "<html>NamDesktop is in monitoring mode.<br>" +
                "Manual edits here may conflict with AI changes in progress.<br><br>" +
                "<i>Choosing Continue suppresses this warning for the rest of the session.</i></html>");

        var optPane = new JOptionPane(message, JOptionPane.WARNING_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null,
                new Object[]{exitBtn, continueBtn, cancelBtn}, continueBtn);
        var dialog = optPane.createDialog(parent, "Monitoring mode is active");
        dialog.getRootPane().setDefaultButton(continueBtn);

        int[] choice = {2}; // default: cancel
        exitBtn    .addActionListener(e -> { choice[0] = 0; dialog.dispose(); });
        continueBtn.addActionListener(e -> { choice[0] = 1; dialog.dispose(); });
        cancelBtn  .addActionListener(e -> { choice[0] = 2; dialog.dispose(); });

        dialog.setVisible(true);

        return switch (choice[0]) {
            case 0 -> { exitCallback.run(); yield true; }
            case 1 -> { sessionAcknowledged = true; yield true; }
            default -> false;
        };
    }
}
