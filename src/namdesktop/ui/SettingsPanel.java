package namdesktop.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import namdesktop.app.AppSettings;
import namdesktop.app.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;

public final class SettingsPanel extends JPanel {

    public SettingsPanel(AppSettings settings, Runnable onChanged) {
        super(new BorderLayout());

        var cards = new JPanel(new CardLayout());
        cards.setBorder(new EmptyBorder(12, 16, 12, 16));
        cards.add(buildAppearance(settings, onChanged), "Appearance");
        cards.add(buildWorkspace(settings),             "Workspace");
        cards.add(buildSync(settings, onChanged),       "Sync");

        var sections = new JList<>(new String[]{"Appearance", "Workspace", "Sync"});
        sections.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sections.setSelectedIndex(0);
        sections.setFixedCellWidth(130);
        sections.setBorder(new EmptyBorder(8, 8, 8, 8));
        sections.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            var name = sections.getSelectedValue();
            if (name != null) ((CardLayout) cards.getLayout()).show(cards, name);
        });

        var sidebar = new JScrollPane(sections,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1,
                UIManager.getColor("Separator.foreground")));

        add(sidebar, BorderLayout.WEST);
        add(cards,   BorderLayout.CENTER);
    }

    private JPanel buildAppearance(AppSettings settings, Runnable onChanged) {
        var p = sectionPanel();
        var gbc = sectionGbc();

        row(p, gbc, 0, "Theme:", () -> {
            var combo = new JComboBox<>(Theme.values());
            combo.setSelectedItem(settings.getTheme());
            combo.addActionListener(e -> {
                var selected = (Theme) combo.getSelectedItem();
                if (selected == null) return;
                settings.setTheme(selected);
                applyTheme(selected);
                save(settings);
                onChanged.run();
            });
            return combo;
        });

        row(p, gbc, 1, "Dense mode:", () -> {
            var box = new JCheckBox("Icons only (hide labels)", settings.isDense());
            box.addActionListener(e -> {
                settings.setDense(box.isSelected());
                UiHelper.applyDense(box.isSelected());
                save(settings);
                onChanged.run();
            });
            return box;
        });

        row(p, gbc, 2, "Status column:", () -> {
            var box = new JCheckBox("Always show status column in Next Actions and Backlog", settings.isShowStatusColumn());
            box.addActionListener(e -> {
                settings.setShowStatusColumn(box.isSelected());
                save(settings);
                onChanged.run();
            });
            return box;
        });

        row(p, gbc, 3, "Start maximized:", () -> {
            var box = new JCheckBox("Launch app in maximized window", settings.isStartMaximized());
            box.addActionListener(e -> {
                settings.setStartMaximized(box.isSelected());
                save(settings);
            });
            return box;
        });

        return p;
    }

    private JPanel buildWorkspace(AppSettings settings) {
        var p = sectionPanel();
        var gbc = sectionGbc();

        row(p, gbc, 0, "Power user mode:", () -> {
            var box = new JCheckBox("Show inline Rename, Description, Delete and tag buttons in Project Workbench", settings.isPowerMode());
            box.addActionListener(e -> {
                settings.setPowerMode(box.isSelected());
                save(settings);
            });
            return box;
        });

        row(p, gbc, 1, "Click to rename:", () -> {
            var box = new JCheckBox("Click on a selected row title to start renaming", settings.isClickToRename());
            box.addActionListener(e -> {
                settings.setClickToRename(box.isSelected());
                save(settings);
            });
            return box;
        });

        return p;
    }

    private JPanel buildSync(AppSettings settings, Runnable onChanged) {
        var p = sectionPanel();
        var gbc = sectionGbc();

        header(p, gbc, 0, "Git sync");

        row(p, gbc, 1, "Sync repo URL:", () -> {
            var field = new JTextField(settings.getSyncRepoUrl(), 30);
            field.setToolTipText("GitHub repo URL for workspace sync, e.g. https://github.com/user/my-workspace");
            field.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusLost(java.awt.event.FocusEvent e) { saveSyncUrl(settings, field); }
            });
            field.addActionListener(e -> saveSyncUrl(settings, field));
            return field;
        });

        var cloud = settings.getCloudSync();

        header(p, gbc, 2, "Cloud sync (Supabase)");

        row(p, gbc, 3, "Enable:", () -> {
            var box = new JCheckBox("Push and pull the workspace via Supabase", cloud.isEnabled());
            box.addActionListener(e -> {
                cloud.setEnabled(box.isSelected());
                save(settings);
                onChanged.run();
            });
            return box;
        });

        row(p, gbc, 4, "Supabase URL:", () -> cloudTextField(settings,
                cloud.getSupabaseUrl(), "Local stack: http://127.0.0.1:54321 — or your hosted project URL",
                cloud::setSupabaseUrl));

        row(p, gbc, 5, "Publishable key:", () -> cloudTextField(settings,
                cloud.getPublishableKey(), "sb_publishable_… key — from `supabase status` (local) or the dashboard (hosted)",
                cloud::setPublishableKey));

        row(p, gbc, 6, "Email:", () -> cloudTextField(settings,
                cloud.getEmail(), "Supabase account email", cloud::setEmail));

        row(p, gbc, 7, "Password:", () -> {
            var field = new JPasswordField(cloud.getPassword(), 30);
            var echo = field.getEchoChar();
            Runnable commit = () -> {
                cloud.setPassword(new String(field.getPassword()));
                save(settings);
            };
            field.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusLost(java.awt.event.FocusEvent e) { commit.run(); }
            });
            field.addActionListener(e -> commit.run());
            var show = new JToggleButton("Show");
            show.addActionListener(e -> field.setEchoChar(show.isSelected() ? (char) 0 : echo));
            var wrap = new JPanel(new BorderLayout(4, 0));
            wrap.add(field, BorderLayout.CENTER);
            wrap.add(show,  BorderLayout.EAST);
            return wrap;
        });

        note(p, gbc, 8, "Credentials are stored locally and only sent to the configured Supabase instance.");

        return p;
    }

    private JTextField cloudTextField(AppSettings settings, String value, String tooltip,
                                      java.util.function.Consumer<String> setter) {
        var field = new JTextField(value, 30);
        field.setToolTipText(tooltip);
        Runnable commit = () -> {
            setter.accept(field.getText());
            save(settings);
        };
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { commit.run(); }
        });
        field.addActionListener(e -> commit.run());
        return field;
    }

    private static JPanel sectionPanel() {
        var p = new JPanel(new GridBagLayout());
        p.setAlignmentY(Component.TOP_ALIGNMENT);
        return p;
    }

    private static GridBagConstraints sectionGbc() {
        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
    }

    private static void header(JPanel p, GridBagConstraints gbc, int row, String title) {
        var label = new JLabel(title);
        label.putClientProperty("FlatLaf.styleClass", "h4");
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        gbc.insets = new Insets(row == 0 ? 4 : 16, 4, 4, 4);
        p.add(label, gbc);
        gbc.gridwidth = 1;
        gbc.insets = new Insets(4, 4, 4, 4);
    }

    private static void note(JPanel p, GridBagConstraints gbc, int row, String text) {
        var label = new JLabel(text);
        label.putClientProperty("FlatLaf.styleClass", "small");
        label.setEnabled(false);
        gbc.gridx = 1; gbc.gridy = row; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        p.add(label, gbc);
    }

    private static void row(JPanel p, GridBagConstraints gbc, int row, String label,
                            java.util.function.Supplier<JComponent> control) {
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        p.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        p.add(control.get(), gbc);
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
