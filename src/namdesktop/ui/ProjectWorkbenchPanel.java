package namdesktop.ui;

import namdesktop.lens.ChildSection;
import namdesktop.lens.ProjectWorkbenchLens;
import namdesktop.lens.WorkbenchProjection;
import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.service.NamWorkspaceService;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class ProjectWorkbenchPanel extends JPanel {

    private final Window            parent;
    private final NamWorkspace      workspace;
    private final NamWorkspaceService service;
    private final Runnable          onNavigateToProjects;
    private       UUID              currentProjectId;
    private       UUID              pendingSelection;

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
                var ancestorEdit = UiHelper.iconOnlyButton("Edit project: " + node.getTitle(),
                        new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/pencil.svg")).derive(14, 14));
                ancestorEdit.addActionListener(e ->
                        new ProjectDialog(parent, id, workspace, service, this::rebuild).setVisible(true));
                crumbs.add(ancestorEdit);
            } else {
                crumbs.add(new JLabel(node.getTitle()));
            }
        }

        var projectName = workspace.getNode(currentProjectId).map(n -> n.getTitle()).orElse("this project");
        var newProjectButton = UiHelper.iconButton("New project",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/folder-plus.svg")).derive(16, 16));
        newProjectButton.setToolTipText("Makes new sub project of this project (" + projectName + ")");
        newProjectButton.addActionListener(e -> {
            var title = JOptionPane.showInputDialog(parent, "Project title:", "New project", JOptionPane.PLAIN_MESSAGE);
            if (title == null || title.isBlank()) return;
            try {
                service.addSubProject(currentProjectId, title.strip());
                rebuild();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(parent, "Failed to save: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        var editButton = UiHelper.iconButton("Edit project…",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/pencil.svg")).derive(16, 16));
        editButton.setToolTipText("Edit project " + projectName);
        editButton.addActionListener(e ->
                new ProjectDialog(parent, currentProjectId, workspace, service, this::rebuild).setVisible(true));

        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.add(newProjectButton);
        buttons.add(editButton);

        bar.add(crumbs,   BorderLayout.CENTER);
        bar.add(buttons,  BorderLayout.EAST);
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

        content.add(buildSection(null, projection.directActions(), currentProjectId, -1, 0, false, false));

        var sections = projection.childSections();
        for (int i = 0; i < sections.size(); i++) {
            var section = sections.get(i);
            var hasSubProjects = workspace.getChildren(section.project().getId())
                    .stream().anyMatch(NamNode::isProject);
            content.add(Box.createVerticalStrut(16));
            content.add(buildSection(section.project().getTitle(), section.directActions(),
                    section.project().getId(), i, sections.size(), hasSubProjects, true));
        }

        return content;
    }

    private JPanel buildSection(String title, List<NamNode> actions, UUID targetProjectId,
                                int sectionIndex, int sectionCount, boolean hasSubProjects, boolean showEditButton) {
        var section = new JPanel() {
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        section.setLayout(new BorderLayout());
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (title != null) section.add(
                buildSectionHeader(title, targetProjectId, sectionIndex, sectionCount, hasSubProjects), BorderLayout.NORTH);

        JList<NamNode> actionList = null;
        JComponent listContent;
        if (actions.isEmpty()) {
            var lbl = new JLabel("  No actions");
            lbl.setForeground(UIManager.getColor("Label.disabledForeground"));
            lbl.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            listContent = lbl;
        } else {
            actionList = buildActionList(actions, targetProjectId);
            listContent = actionList;
        }

        var listWrapper = new JPanel(new BorderLayout());
        listWrapper.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")));
        listWrapper.add(listContent, BorderLayout.CENTER);
        section.add(listWrapper, BorderLayout.CENTER);

        section.add(buildAddActionBar(targetProjectId, actionList, showEditButton), BorderLayout.SOUTH);

        return section;
    }

    private JPanel buildAddActionBar(UUID targetProjectId, JList<NamNode> actionList, boolean showEditButton) {
        var targetName = workspace.getNode(targetProjectId).map(n -> n.getTitle()).orElse("this project");
        var addActionButton = UiHelper.iconButton("Add action",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/plus.svg")).derive(16, 16));
        addActionButton.setToolTipText("Add action to project " + targetName);
        addActionButton.addActionListener(e -> {
            var title = JOptionPane.showInputDialog(parent, "Action title:", "Add action", JOptionPane.PLAIN_MESSAGE);
            if (title == null || title.isBlank()) return;
            try {
                service.addChild(targetProjectId, title.strip());
                rebuild();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(parent, "Failed to save: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        var bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        bar.add(addActionButton);

        if (showEditButton) {
            var editButton = UiHelper.iconButton("Edit project…",
                    new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/pencil.svg")).derive(16, 16));
            editButton.setToolTipText("Edit project: " + targetName);
            editButton.addActionListener(e ->
                    new ProjectDialog(SwingUtilities.getWindowAncestor(this), targetProjectId, workspace, service, this::rebuild)
                            .setVisible(true));
            bar.add(editButton);
        }

        if (actionList != null) {
            var upButton   = UiHelper.iconButton("Move up",
                    new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/arrow-up.svg")).derive(16, 16));
            var downButton = UiHelper.iconButton("Move down",
                    new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/arrow-down.svg")).derive(16, 16));
            upButton.setToolTipText("Move selected action up");
            downButton.setToolTipText("Move selected action down");
            upButton.setEnabled(false);
            downButton.setEnabled(false);

            actionList.addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                var idx  = actionList.getSelectedIndex();
                var size = actionList.getModel().getSize();
                upButton.setEnabled(idx > 0);
                downButton.setEnabled(idx >= 0 && idx < size - 1);
            });

            // initialise button state for any selection already set (e.g. restored after rebuild)
            var restoredIdx = actionList.getSelectedIndex();
            upButton.setEnabled(restoredIdx > 0);
            downButton.setEnabled(restoredIdx >= 0 && restoredIdx < actionList.getModel().getSize() - 1);

            upButton.addActionListener(e -> {
                var node = actionList.getSelectedValue();
                if (node == null) return;
                try { pendingSelection = node.getId(); service.moveActionUp(targetProjectId, node.getId()); rebuild(); }
                catch (IOException ex) { showError(ex.getMessage()); }
            });
            downButton.addActionListener(e -> {
                var node = actionList.getSelectedValue();
                if (node == null) return;
                try { pendingSelection = node.getId(); service.moveActionDown(targetProjectId, node.getId()); rebuild(); }
                catch (IOException ex) { showError(ex.getMessage()); }
            });

            bar.add(upButton);
            bar.add(downButton);
        }

        return bar;
    }

    private JComponent buildSectionHeader(String title, UUID navigateToId,
                                          int sectionIndex, int sectionCount, boolean hasSubProjects) {
        var header = new JPanel(new BorderLayout());

        var btn = new JButton(title + " ›");
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        var style = Font.BOLD | (hasSubProjects ? Font.ITALIC : Font.PLAIN);
        btn.setFont(btn.getFont().deriveFont((float) btn.getFont().getSize()).deriveFont(style));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> navigateTo(navigateToId));

        var upButton = UiHelper.iconButton("Move section up",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/arrow-up.svg")).derive(16, 16));
        var downButton = UiHelper.iconButton("Move section down",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/arrow-down.svg")).derive(16, 16));
        upButton.setToolTipText("Move this sub-project up");
        downButton.setToolTipText("Move this sub-project down");
        upButton.setEnabled(sectionIndex > 0);
        downButton.setEnabled(sectionIndex < sectionCount - 1);

        upButton.addActionListener(e -> {
            try { service.moveProjectUp(currentProjectId, navigateToId); rebuild(); }
            catch (IOException ex) { showError(ex.getMessage()); }
        });
        downButton.addActionListener(e -> {
            try { service.moveProjectDown(currentProjectId, navigateToId); rebuild(); }
            catch (IOException ex) { showError(ex.getMessage()); }
        });

        var orderButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        orderButtons.add(upButton);
        orderButtons.add(downButton);

        header.add(btn,          BorderLayout.CENTER);
        header.add(orderButtons, BorderLayout.EAST);
        return header;
    }

    private JList<NamNode> buildActionList(List<NamNode> actions, UUID targetProjectId) {
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

        if (pendingSelection != null) {
            for (int i = 0; i < model.getSize(); i++) {
                if (model.getElementAt(i).getId().equals(pendingSelection)) {
                    list.setSelectedIndex(i);
                    pendingSelection = null;
                    break;
                }
            }
        }

        return list;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(parent, "Failed to save: " + message, "Error", JOptionPane.ERROR_MESSAGE);
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
