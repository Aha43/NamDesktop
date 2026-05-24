package namdesktop.ui;

import namdesktop.app.AppSettings;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;

public final class UiHelper {

    private static final String PROP_DENSEABLE = "namdesktop.denseable";
    private static final String PROP_LABEL     = "namdesktop.label";

    private UiHelper() {}

    public static JButton iconButton(String label, Icon icon) {
        var settings = AppSettings.getInstance();
        var dense    = settings != null && settings.isDense();
        var btn      = new JButton(dense ? "" : label, icon);
        btn.setToolTipText(label);
        btn.putClientProperty(PROP_DENSEABLE, Boolean.TRUE);
        btn.putClientProperty(PROP_LABEL, label);
        return btn;
    }

    /** Always icon-only regardless of dense mode — use for compact inline contexts like breadcrumbs. */
    public static JButton iconOnlyButton(String label, Icon icon) {
        var btn = new JButton("", icon);
        btn.setToolTipText(label);
        return btn;
    }

    public static DefaultTableCellRenderer tagsRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public void setValue(Object value) {
                if (value instanceof String[] pair) {
                    var own       = pair[0];
                    var inherited = pair[1];
                    if (own.isEmpty() && inherited.isEmpty()) { setText(""); return; }
                    if (inherited.isEmpty()) { setText(own); return; }
                    if (own.isEmpty()) { setText("<html><i>" + inherited + "</i></html>"); return; }
                    setText("<html>" + own + ", <i>" + inherited + "</i></html>");
                } else {
                    setText(value != null ? value.toString() : "");
                }
            }
        };
    }

    public static void applyDense(boolean dense) {
        for (var w : Window.getWindows()) applyDenseToContainer(w, dense);
    }

    private static void applyDenseToContainer(Container c, boolean dense) {
        for (var comp : c.getComponents()) {
            if (comp instanceof JButton btn && Boolean.TRUE.equals(btn.getClientProperty(PROP_DENSEABLE))) {
                btn.setText(dense ? "" : (String) btn.getClientProperty(PROP_LABEL));
            }
            if (comp instanceof Container sub) applyDenseToContainer(sub, dense);
        }
    }
}
