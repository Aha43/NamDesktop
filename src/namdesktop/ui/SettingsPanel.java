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

    public SettingsPanel(AppSettings settings, Runnable onChanged) {
        super(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Theme
        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("Theme:"), gbc);

        var themeCombo = new JComboBox<>(Theme.values());
        themeCombo.setSelectedItem(settings.getTheme());
        themeCombo.addActionListener(e -> {
            var selected = (Theme) themeCombo.getSelectedItem();
            if (selected == null) return;
            settings.setTheme(selected);
            applyTheme(selected);
            save(settings);
            onChanged.run();
        });

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        add(themeCombo, gbc);

        // Dense mode
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        add(new JLabel("Dense mode:"), gbc);

        var denseBox = new JCheckBox("Icons only (hide labels)", settings.isDense());
        denseBox.addActionListener(e -> {
            settings.setDense(denseBox.isSelected());
            UiHelper.applyDense(denseBox.isSelected());
            save(settings);
            onChanged.run();
        });

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        add(denseBox, gbc);

        // Always show status column
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        add(new JLabel("Status column:"), gbc);

        var statusBox = new JCheckBox("Always show status column in Next Actions and Backlog", settings.isShowStatusColumn());
        statusBox.addActionListener(e -> {
            settings.setShowStatusColumn(statusBox.isSelected());
            save(settings);
            onChanged.run();
        });

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        add(statusBox, gbc);

        // Start maximized
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        add(new JLabel("Start maximized:"), gbc);

        var maximizedBox = new JCheckBox("Launch app in maximized window", settings.isStartMaximized());
        maximizedBox.addActionListener(e -> {
            settings.setStartMaximized(maximizedBox.isSelected());
            save(settings);
        });

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        add(maximizedBox, gbc);

        // Power user mode
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        add(new JLabel("Power user mode:"), gbc);

        var powerBox = new JCheckBox("Show inline Rename, Description, Delete and tag buttons in Project Workbench", settings.isPowerMode());
        powerBox.addActionListener(e -> {
            settings.setPowerMode(powerBox.isSelected());
            save(settings);
            onChanged.run();
        });

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        add(powerBox, gbc);

        // Sync repo URL
        gbc.gridx = 0; gbc.gridy = 5; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        add(new JLabel("Sync repo URL:"), gbc);

        var syncUrlField = new JTextField(settings.getSyncRepoUrl(), 30);
        syncUrlField.setToolTipText("GitHub repo URL for workspace sync, e.g. https://github.com/user/my-workspace");
        syncUrlField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { saveSyncUrl(settings, syncUrlField); }
        });
        syncUrlField.addActionListener(e -> saveSyncUrl(settings, syncUrlField));

        gbc.gridx = 1; gbc.gridy = 5; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        add(syncUrlField, gbc);
    }

    public SettingsPanel(AppSettings settings) {
        this(settings, () -> {});
    }

    private void saveSyncUrl(AppSettings settings, JTextField field) {
        settings.setSyncRepoUrl(field.getText());
        save(settings);
    }

    private void save(AppSettings settings) {
        try {
            settings.save();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save settings: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    static void applyTheme(Theme theme) {
        if (theme == Theme.LIGHT) FlatLightLaf.setup(); else FlatDarkLaf.setup();
        FlatLaf.updateUI();
        for (var w : Window.getWindows()) SwingUtilities.updateComponentTreeUI(w);
    }
}
