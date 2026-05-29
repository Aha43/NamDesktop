package namdesktop.ui;

import namdesktop.service.MonitoringMode;

import javax.swing.*;
import java.awt.*;

public final class MonitoringExitDialog extends JDialog {

    private boolean accepted = false;

    private MonitoringExitDialog(Window parent, MonitoringMode.DiffSummary summary, String title) {
        super(parent, title, ModalityType.APPLICATION_MODAL);

        var message = new JLabel("<html><b>Changes detected:</b><br><br>" + summary.describe() + "</html>");
        message.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        var acceptButton = new JButton("Accept");
        var rejectButton = new JButton("Reject");
        acceptButton.addActionListener(e -> { accepted = true;  dispose(); });
        rejectButton.addActionListener(e -> { accepted = false; dispose(); });
        getRootPane().setDefaultButton(acceptButton);

        var footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(rejectButton);
        footer.add(acceptButton);

        setLayout(new BorderLayout());
        add(message, BorderLayout.CENTER);
        add(footer,  BorderLayout.SOUTH);
        setSize(400, 180);
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    public static boolean show(Window parent, MonitoringMode.DiffSummary summary) {
        return show(parent, summary, "Exit Monitoring Mode");
    }

    public static boolean show(Window parent, MonitoringMode.DiffSummary summary, String title) {
        var dialog = new MonitoringExitDialog(parent, summary, title);
        dialog.setVisible(true);
        return dialog.accepted;
    }
}
