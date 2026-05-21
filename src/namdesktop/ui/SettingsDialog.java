package namdesktop.ui;

import namdesktop.app.AppSettings;

import javax.swing.*;
import java.awt.*;

public final class SettingsDialog extends JDialog {

    public SettingsDialog(Window parent, AppSettings settings) {
        super(parent, "Settings", ModalityType.APPLICATION_MODAL);

        var closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(closeButton);

        var footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(closeButton);

        setLayout(new BorderLayout());
        add(new SettingsPanel(settings), BorderLayout.CENTER);
        add(footer,                      BorderLayout.SOUTH);

        pack();
        setResizable(false);
        setLocationRelativeTo(parent);
    }
}
