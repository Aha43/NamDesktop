package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.lens.MissionControlLens;
import namdesktop.lens.MissionControlStation;
import namdesktop.model.MissionControl;
import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.util.UUID;
import java.util.function.Consumer;

public final class MissionControlPanel extends JPanel {

    private static final Color COLOR_RED     = new Color(180,  60,  60);
    private static final Color COLOR_AMBER   = new Color(190, 130,   0);
    private static final Color COLOR_GREEN   = new Color( 50, 150,  50);
    private static final Color COLOR_NEUTRAL = new Color(110, 110, 110);

    private final MissionControl      mc;
    private final NamWorkspace        workspace;
    private final NamWorkspaceService service;
    private final Consumer<UUID>      onOpenProject;
    private final Runnable            onDeleted;
    private final JPanel              grid;

    public MissionControlPanel(MissionControl mc, NamWorkspace workspace,
                               NamWorkspaceService service,
                               Consumer<UUID> onOpenProject,
                               Runnable onDeleted) {
        super(new BorderLayout());
        this.mc            = mc;
        this.workspace     = workspace;
        this.service       = service;
        this.onOpenProject = onOpenProject;
        this.onDeleted     = onDeleted;

        var deleteButton = UiHelper.iconButton("Delete Goal Board",
                new FlatSVGIcon(MissionControlPanel.class.getResource("/icons/trash.svg")).derive(16, 16));
        deleteButton.setToolTipText("Delete this Goal Board");
        deleteButton.addActionListener(e -> deleteSelf());

        var toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(deleteButton);
        add(toolbar, BorderLayout.NORTH);

        grid = new JPanel(new WrapLayout(FlowLayout.LEFT, 12, 12));
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        var scroll = new JScrollPane(grid);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);

        refresh();
    }

    public void refresh() {
        var stations = new MissionControlLens().stations(mc, workspace);
        grid.removeAll();
        if (stations.isEmpty()) {
            var label = new JLabel(
                    "No projects tagged with " + String.join(", ", mc.tags()) + " found.",
                    SwingConstants.CENTER);
            label.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
            grid.add(label);
        } else {
            for (var s : stations) grid.add(buildCard(s));
        }
        grid.revalidate();
        grid.repaint();
    }

    private JPanel buildCard(MissionControlStation s) {
        var card = new JPanel(new BorderLayout(0, 6));
        card.setPreferredSize(new Dimension(200, 160));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(heatColor(s.heatLevel()), 3),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        var title = new JLabel("<html><center>" + s.title() + "</center></html>", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));

        var stats = new JPanel();
        stats.setLayout(new BoxLayout(stats, BoxLayout.PAGE_AXIS));
        stats.setOpaque(false);
        stats.add(stat(s.subProjectCount() + " sub-project" + (s.subProjectCount() != 1 ? "s" : "")));
        stats.add(stat("Max depth: " + s.maxDepth()));
        stats.add(stat("Done: " + s.doneCount() + " / " + s.totalActions()));
        if (s.rolledUpCount() > 0) {
            stats.add(Box.createVerticalStrut(4));
            var note = new JLabel("Includes " + s.rolledUpCount() + " tagged sub-project"
                    + (s.rolledUpCount() != 1 ? "s" : ""), SwingConstants.CENTER);
            note.setFont(note.getFont().deriveFont(Font.ITALIC, 11f));
            note.setForeground(UIManager.getColor("Label.disabledForeground"));
            note.setAlignmentX(CENTER_ALIGNMENT);
            stats.add(note);
        }

        card.add(title, BorderLayout.NORTH);
        card.add(stats, BorderLayout.CENTER);
        var pct     = (int) Math.round(s.doneRatio() * 100);
        var heatNote = s.heatLevel() == MissionControlStation.HeatLevel.NEUTRAL
                ? "<b>No actions yet</b>"
                : "<b>" + pct + "% of actions done</b>";
        var tooltip = "<html>" + heatNote
                + "<br>Green ≥ 67% · Amber ≥ 33% · Red &lt; 33% · Gray = no actions yet"
                + "<br><i>Click to open workbench</i></html>";
        addClickHandler(card, () -> onOpenProject.accept(s.id()), tooltip);
        return card;
    }

    private void addClickHandler(java.awt.Component c, Runnable onClick, String tooltip) {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (c instanceof JComponent jc) jc.setToolTipText(tooltip);
        c.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { onClick.run(); }
        });
        if (c instanceof Container container) {
            for (var child : container.getComponents()) addClickHandler(child, onClick, tooltip);
        }
    }

    private JLabel stat(String text) {
        var label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(12f));
        label.setAlignmentX(CENTER_ALIGNMENT);
        return label;
    }

    private Color heatColor(MissionControlStation.HeatLevel level) {
        return switch (level) {
            case GREEN   -> COLOR_GREEN;
            case AMBER   -> COLOR_AMBER;
            case RED     -> COLOR_RED;
            case NEUTRAL -> COLOR_NEUTRAL;
        };
    }

    private void deleteSelf() {
        var confirm = JOptionPane.showConfirmDialog(this,
                "Delete Goal Board \"" + mc.name() + "\"?",
                "Delete", JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) return;
        try {
            service.deleteMissionControl(mc.name());
            onDeleted.run();
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // FlowLayout that recalculates height based on available width
    private static final class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

        @Override public Dimension preferredLayoutSize(Container t) { return layoutSize(t, true); }
        @Override public Dimension minimumLayoutSize(Container t)   { return layoutSize(t, false); }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                var w = target.getSize().width;
                if (w == 0) w = Integer.MAX_VALUE;
                var ins = target.getInsets();
                var maxW = w - ins.left - ins.right - getHgap() * 2;
                var dim  = new Dimension(0, 0);
                int rowW = 0, rowH = 0;
                for (int i = 0; i < target.getComponentCount(); i++) {
                    var c = target.getComponent(i);
                    if (!c.isVisible()) continue;
                    var d = preferred ? c.getPreferredSize() : c.getMinimumSize();
                    if (rowW + d.width > maxW && rowW > 0) {
                        dim.width  = Math.max(dim.width, rowW);
                        dim.height += rowH + getVgap();
                        rowW = 0; rowH = 0;
                    }
                    rowW += d.width + getHgap();
                    rowH  = Math.max(rowH, d.height);
                }
                dim.width  = Math.max(dim.width, rowW);
                dim.height += rowH + getVgap() * 2 + ins.top + ins.bottom;
                return dim;
            }
        }
    }
}
