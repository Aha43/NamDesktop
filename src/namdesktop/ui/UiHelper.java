package namdesktop.ui;

import namdesktop.app.AppSettings;

import javax.swing.*;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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

    /**
     * Makes {@code fillCol} absorb all slack space as the table is resized or columns are
     * added/removed (e.g. toggling the Status column). Other columns keep their preferred widths.
     * Listens on the viewport (table's parent) because with AUTO_RESIZE_OFF the table itself
     * does not stretch — only the viewport does.
     */
    public static void fillTableColumn(JTable table, int fillCol) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        Runnable recalc = () -> {
            var port = table.getParent();
            int available = (port != null && port.getWidth() > 0) ? port.getWidth() : table.getWidth();
            if (available <= 0) return;
            var cm = table.getColumnModel();
            if (fillCol >= cm.getColumnCount()) return;
            int reserved = 0;
            for (int i = 0; i < cm.getColumnCount(); i++) {
                if (i != fillCol) reserved += cm.getColumn(i).getPreferredWidth();
            }
            int w = Math.max(60, available - reserved);
            var col = cm.getColumn(fillCol);
            col.setPreferredWidth(w);
            col.setWidth(w);
        };
        // Wire viewport listener once the table is added to a scroll pane
        table.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.PARENT_CHANGED) != 0
                    && table.getParent() != null) {
                table.getParent().addComponentListener(new ComponentAdapter() {
                    @Override public void componentResized(ComponentEvent e2) { recalc.run(); }
                });
                recalc.run();
            }
        });
        table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            public void columnAdded(TableColumnModelEvent e)                 { recalc.run(); }
            public void columnRemoved(TableColumnModelEvent e)               { recalc.run(); }
            public void columnMoved(TableColumnModelEvent e)                 {}
            public void columnMarginChanged(javax.swing.event.ChangeEvent e) {}
            public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) {}
        });
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
