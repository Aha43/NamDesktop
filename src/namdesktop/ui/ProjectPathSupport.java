package namdesktop.ui;

import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Makes an action's project path ("Trip to Rome &gt; Flights") a clickable breadcrumb: each
 * segment navigates to that ancestor project (#382). Shared across the list panels (table cells)
 * and the focus deck ({@link MoonCardPanel}, link labels).
 *
 * <p>Segments mirror the lens' {@code buildProjectPath}: the node's ancestor chain, root→leaf,
 * with the structural area nodes (root/inbox/projects/actions) removed, joined by {@code " > "}.
 */
public final class ProjectPathSupport {

    /** A single breadcrumb segment: a project title and the id to open when clicked. */
    public record Segment(String title, UUID id) {}

    public static final String SEPARATOR = " > ";

    private ProjectPathSupport() {}

    private static Set<UUID> structuralIds(NamWorkspace ws) {
        return Stream.of(ws.getRootNodeId(), ws.getInboxNodeId(),
                        ws.getProjectsNodeId(), ws.getNextActionsNodeId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** Breadcrumb segments for the project that owns {@code actionId}; empty for free actions. */
    public static List<Segment> forAction(NamWorkspace ws, UUID actionId) {
        if (actionId == null) return List.of();
        var parent = ws.getParent(actionId).orElse(null);
        if (parent == null) return List.of();
        var structural = structuralIds(ws);
        var segs = new ArrayList<Segment>();
        for (NamNode node : ws.buildPath(parent.getId())) {
            if (structural.contains(node.getId())) continue;
            segs.add(new Segment(node.getTitle(), node.getId()));
        }
        return List.copyOf(segs);
    }

    /**
     * Which segment index the offset {@code rel} (pixels from the start of the rendered path text)
     * falls on, or -1 if before the first segment. Pure: the width measurements are injected so the
     * geometry can be unit-tested without a toolkit. A click in a {@code " > "} separator is
     * attributed to the segment on its left.
     */
    public static int segmentIndexAt(int rel, List<String> titles, ToIntFunction<String> widthOf, int sepWidth) {
        if (rel < 0 || titles.isEmpty()) return -1;
        int x = 0;
        for (int i = 0; i < titles.size(); i++) {
            int segW = widthOf.applyAsInt(titles.get(i));
            if (rel <= x + segW) return i;
            x += segW + sepWidth;
            if (rel <= x) return i; // inside the separator after segment i
        }
        return titles.size() - 1; // past the end (trailing padding) → last segment
    }

    /** The segment id hit by a click at {@code clickX} within a table cell, or null. */
    public static UUID segmentAt(JTable table, int row, int col, int clickX, List<Segment> segs) {
        if (segs.isEmpty()) return null;
        var rect = table.getCellRect(row, col, false);
        var comp = table.prepareRenderer(table.getCellRenderer(row, col), row, col);
        int leftInset = 0;
        FontMetrics fm;
        if (comp instanceof JComponent jc) {
            leftInset = jc.getInsets().left;
            fm = jc.getFontMetrics(jc.getFont());
        } else {
            fm = table.getFontMetrics(table.getFont());
        }
        int rel = clickX - rect.x - leftInset;
        var titles = segs.stream().map(Segment::title).toList();
        int idx = segmentIndexAt(rel, titles, fm::stringWidth, fm.stringWidth(SEPARATOR));
        if (idx < 0) idx = 0; // click in the left padding → first segment
        return segs.get(idx).id();
    }

    /** Link color for the path, adapting to the active theme. */
    public static Color linkColor() {
        var c = UIManager.getColor("Component.linkColor");
        if (c == null) c = UIManager.getColor("Hyperlink.linkColor");
        return c != null ? c : new Color(0x58, 0x9D, 0xF6);
    }

    /**
     * Styles {@code col} of {@code table} as a link: link-colored text and a hand cursor when
     * hovering a non-empty path cell. Click handling is left to the panel (it knows the row→action
     * mapping); call {@link #segmentAt} from there.
     */
    public static void installLinkColumn(JTable table, int col) {
        var base = table.getColumnModel().getColumn(col).getCellRenderer();
        table.getColumnModel().getColumn(col).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean sel,
                                                                     boolean focus, int row, int column) {
                var c = base != null
                        ? base.getTableCellRendererComponent(t, value, sel, focus, row, column)
                        : super.getTableCellRendererComponent(t, value, sel, focus, row, column);
                if (!sel && value != null && !value.toString().isBlank() && c instanceof JComponent jc)
                    jc.setForeground(linkColor());
                return c;
            }
        });
        table.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                var r = table.rowAtPoint(e.getPoint());
                var cAt = table.columnAtPoint(e.getPoint());
                boolean overLink = r >= 0 && cAt == col
                        && table.getValueAt(r, col) != null
                        && !table.getValueAt(r, col).toString().isBlank();
                table.setCursor(Cursor.getPredefinedCursor(overLink ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        });
    }
}
