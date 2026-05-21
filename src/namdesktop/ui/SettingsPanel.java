package namdesktop.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import namdesktop.app.AppSettings;
import namdesktop.app.Theme;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public final class SettingsPanel extends JPanel {

    public SettingsPanel(AppSettings settings) {
        super(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("Theme:"), gbc);

        var themeCombo = new JComboBox<>(Theme.values());
        themeCombo.setSelectedItem(settings.getTheme());
        themeCombo.addActionListener(e -> {
            var selected = (Theme) themeCombo.getSelectedItem();
            if (selected == null) return;
            settings.setTheme(selected);
            applyTheme(selected);
            try {
                settings.save();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to save settings: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        add(themeCombo, gbc);
    }

    static void applyTheme(Theme theme) {
        if (theme == Theme.LIGHT) FlatLightLaf.setup(); else FlatDarkLaf.setup();
        FlatLaf.updateUI();
        for (var w : Window.getWindows()) SwingUtilities.updateComponentTreeUI(w);
    }
}
