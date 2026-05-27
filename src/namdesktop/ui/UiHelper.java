package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.app.AppSettings;
import namdesktop.model.NodeStatus;

import javax.swing.*;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

public final class UiHelper {

    private static final String PROP_DENSEABLE = "namdesktop.denseable";
    private static final String PROP_LABEL     = "namdesktop.label";

    public static final int ACTION_BADGE_W      = 24;
    public static final int ACTION_PENCIL_W     = 18;

    private static final Icon PENCIL_ICON = new FlatSVGIcon(
            UiHelper.class.getResource("/icons/pencil.svg")).derive(12, 12);
    private static final Color BADGE_NEXT       = new Color(50, 150, 80);
    private static final Color BADGE_BACKLOG    = new Color(160, 120, 30);
    private static final Color BADGE_DONE_COLOR = new Color(110, 110, 110);

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

    public static TableCellRenderer actionBadgeRenderer(IntFunction<NodeStatus> statusFn) {
        return actionBadgeRenderer(statusFn, row -> false);
    }

    public static TableCellRenderer actionBadgeRenderer(IntFunction<NodeStatus> statusFn, IntPredicate isBlockedFn) {
        return new TableCellRenderer() {
            private final JPanel cell   = new JPanel(new BorderLayout(4, 0));
            private final JLabel badge  = new JLabel();
            private final JLabel title  = new JLabel();
            private final JLabel pencil = new JLabel(PENCIL_ICON);
            {
                cell.setOpaque(true);
                badge.setOpaque(false);
                title.setOpaque(false);
                pencil.setOpaque(false);
                badge.setPreferredSize(new Dimension(ACTION_BADGE_W, 0));
                pencil.setPreferredSize(new Dimension(ACTION_PENCIL_W, 0));
                pencil.setHorizontalAlignment(SwingConstants.CENTER);
                badge.setFont(badge.getFont().deriveFont(Font.BOLD, 10f));
                badge.setHorizontalAlignment(SwingConstants.CENTER);
                cell.add(badge,  BorderLayout.WEST);
                cell.add(title,  BorderLayout.CENTER);
                cell.add(pencil, BorderLayout.EAST);
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                var bg = isSelected ? table.getSelectionBackground() : table.getBackground();
                var fg = isSelected ? table.getSelectionForeground() : table.getForeground();
                cell.setBackground(bg);
                var status = statusFn.apply(row);
                if (status == null) status = NodeStatus.BACKLOG;
                badge.setText(switch (status) {
                    case NEXT      -> "N";
                    case BACKLOG   -> "B";
                    case DONE      -> "D";
                    case CANCELLED -> "C";
                    case ARCHIVED  -> "A";
                });
                badge.setForeground(isSelected ? table.getSelectionForeground() : switch (status) {
                    case NEXT    -> BADGE_NEXT;
                    case BACKLOG -> BADGE_BACKLOG;
                    default      -> BADGE_DONE_COLOR;
                });
                var dim = !isSelected && (status == NodeStatus.DONE || isBlockedFn.test(row));
                title.setForeground(dim ? BADGE_DONE_COLOR : fg);
                title.setText(value != null ? value.toString() : "");
                return cell;
            }
        };
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
