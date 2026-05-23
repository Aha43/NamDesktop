package namdesktop.ui;

import namdesktop.lens.ChildSection;
import namdesktop.lens.ProjectWorkbenchLens;
import namdesktop.lens.WorkbenchProjection;
import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.UUID;

public final class ProjectWorkbenchPanel extends JPanel {

    private final Window            parent;
    private final NamWorkspace      workspace;
    private final NamWorkspaceService service;
    private final Runnable          onNavigateToProjects;
    private       UUID              currentProjectId;

    public ProjectWorkbenchPanel(Window parent, NamWorkspace workspace,
                                  NamWorkspaceService service, UUID initialProjectId,
                                  Runnable onNavigateToProjects) {
        super(new BorderLayout());
        this.parent               = parent;
        this.workspace            = workspace;
        this.service              = service;
        this.onNavigateToProjects = onNavigateToProjects;
        this.currentProjectId     = initialProjectId;
        rebuild();
    }

    private void rebuild() {
        removeAll();
        var projection = new ProjectWorkbenchLens().project(workspace, currentProjectId);
        add(buildBreadcrumbBar(projection.breadcrumb()), BorderLayout.NORTH);
        add(new JScrollPane(buildContent(projection)),   BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    // --- breadcrumb ---

    private JPanel buildBreadcrumbBar(List<NamNode> breadcrumb) {
        var bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        var crumbs = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        crumbs.add(breadcrumbLink("Projects", onNavigateToProjects));
        for (int i = 0; i < breadcrumb.size(); i++) {
            var node = breadcrumb.get(i);
            crumbs.add(new JLabel(" › "));
            if (i < breadcrumb.size() - 1) {
                final var id = node.getId();
                crumbs.add(breadcrumbLink(node.getTitle(), () -> navigateTo(id)));
            } else {
                crumbs.add(new JLabel(node.getTitle()));
            }
        }

        var editButton = new JButton("Edit project…");
        editButton.addActionListener(e -> {
            new ProjectDialog(parent, currentProjectId, workspace, service, this::rebuild).setVisible(true);
        });

        bar.add(crumbs,      BorderLayout.CENTER);
        bar.add(editButton,  BorderLayout.EAST);
        return bar;
    }

    private static JButton breadcrumbLink(String title, Runnable action) {
        var btn = new JButton(title);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setForeground(UIManager.getColor("Component.linkColor") != null
                ? UIManager.getColor("Component.linkColor")
                : UIManager.getColor("Label.foreground"));
        btn.addActionListener(e -> action.run());
        return btn;
    }

    // --- content ---

    private JPanel buildContent(WorkbenchProjection projection) {
        var content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        content.add(buildSection(null, projection.directActions(), null));

        for (var section : projection.childSections()) {
            content.add(Box.createVerticalStrut(16));
            content.add(buildSection(section.project().getTitle(), section.directActions(), section.project().getId()));
        }

        return content;
    }

    private JPanel buildSection(String title, List<NamNode> actions, UUID childProjectId) {
        var section = new JPanel() {
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        section.setLayout(new BorderLayout());
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (title != null) section.add(buildSectionHeader(title, childProjectId), BorderLayout.NORTH);

        var listWrapper = new JPanel(new BorderLayout());
        listWrapper.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")));
        listWrapper.add(buildActionList(actions), BorderLayout.CENTER);
        section.add(listWrapper, BorderLayout.CENTER);

        return section;
    }

    private JComponent buildSectionHeader(String title, UUID childProjectId) {
        var header = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        if (childProjectId != null) {
            var btn = new JButton(title + " ›");
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setFont(btn.getFont().deriveFont(Font.BOLD));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> navigateTo(childProjectId));
            header.add(btn);
        } else {
            var lbl = new JLabel(title);
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
            header.add(lbl);
        }
        return header;
    }

    private JComponent buildActionList(List<NamNode> actions) {
        if (actions.isEmpty()) {
            var lbl = new JLabel("  No actions");
            lbl.setForeground(UIManager.getColor("Label.disabledForeground"));
            lbl.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            return lbl;
        }

        var model = new DefaultListModel<NamNode>();
        for (var a : actions) model.addElement(a);

        var list = new JList<>(model);
        list.setCellRenderer(new ActionCellRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                var idx = list.locationToIndex(e.getPoint());
                if (idx < 0) return;
                var node = model.getElementAt(idx);
                new ActionDialog(parent, node.getId(), workspace, service, true, ProjectWorkbenchPanel.this::rebuild)
                        .setVisible(true);
            }
        });
        return list;
    }

    private void navigateTo(UUID projectId) {
        currentProjectId = projectId;
        rebuild();
    }

    private static final class ActionCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            var c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof NamNode node && node.getStatus() == NodeStatus.DONE && !isSelected) {
                c.setForeground(Color.GRAY);
            }
            return c;
        }
    }
}
