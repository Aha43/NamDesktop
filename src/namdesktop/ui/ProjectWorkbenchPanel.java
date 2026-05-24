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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ProjectWorkbenchPanel extends JPanel {

    private final Window            parent;
    private final NamWorkspace      workspace;
    private final NamWorkspaceService service;
    private final Runnable          onNavigateToProjects;
    private       UUID              currentProjectId;
    private       UUID              parentProjectId;
    private       UUID              pendingSelection;
    private final Set<UUID>         collapsedSections = new HashSet<>();

    public ProjectWorkbenchPanel(Window parent, NamWorkspace workspace,
                                  NamWorkspaceService service, UUID initialProjectId,
                                  Runnable onNavigateToProjects) {
        super(new BorderLayout());
        this.parent               = parent;
        this.workspace            = workspace;
        this.service              = service;
        this.onNavigateToProjects = onNavigateToProjects;
        this.currentProjectId     = initialProjectId;
        this.parentProjectId      = workspace.getParent(initialProjectId)
                .map(n -> n.getId()).orElse(null);
        rebuild();
    }

    private void rebuild() {
        if (workspace.getNode(currentProjectId).isEmpty()) {
            if (parentProjectId != null
                    && !parentProjectId.equals(workspace.getProjectsNodeId())
                    && workspace.getNode(parentProjectId).isPresent()) {
                currentProjectId = parentProjectId;
                parentProjectId  = workspace.getParent(currentProjectId)
                        .map(n -> n.getId()).orElse(null);
            } else {
                onNavigateToProjects.run();
                return;
            }
        }
        removeAll();
        var projection  = new ProjectWorkbenchLens().project(workspace, currentProjectId);
        var sectionIds  = new java.util.ArrayList<UUID>();
        sectionIds.add(currentProjectId);
        projection.childSections().stream().map(s -> s.project().getId()).forEach(sectionIds::add);
        add(buildBreadcrumbBar(projection.breadcrumb(), sectionIds), BorderLayout.NORTH);
        add(new JScrollPane(buildContent(projection)),                BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    // --- breadcrumb ---

    private JPanel buildBreadcrumbBar(List<NamNode> breadcrumb, List<UUID> sectionIds) {
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
                var newId = service.addSubProject(currentProjectId, title.strip());
                collapsedSections.remove(newId);
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

        var collapseAllIcon = new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/chevron-up.svg")).derive(16, 16);
        var expandAllIcon   = new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/chevron-down.svg")).derive(16, 16);
        var allCollapsed    = !sectionIds.isEmpty() && collapsedSections.containsAll(sectionIds);
        var toggleAllButton = UiHelper.iconOnlyButton(allCollapsed ? "Expand all" : "Collapse all",
                allCollapsed ? expandAllIcon : collapseAllIcon);
        toggleAllButton.addActionListener(e -> {
            var nowAllCollapsed = !sectionIds.isEmpty() && collapsedSections.containsAll(sectionIds);
            if (nowAllCollapsed) collapsedSections.removeAll(sectionIds);
            else                 collapsedSections.addAll(sectionIds);
            rebuild();
        });
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        if (!sectionIds.isEmpty()) {
            buttons.add(toggleAllButton);
        }
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

        content.add(buildSection("Actions", projection.directActions(), currentProjectId, -1, 0, false, true));

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

        var bar = buildAddActionBar(targetProjectId, actionList, showEditButton);

        if (title != null) {
            var collapsed = collapsedSections.contains(targetProjectId);
            listWrapper.setVisible(!collapsed);
            bar.setVisible(!collapsed);

            var collapseIcon   = new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/chevron-down.svg")).derive(14, 14);
            var expandIcon     = new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/chevron-up.svg")).derive(14, 14);
            var toggleButton   = UiHelper.iconOnlyButton(collapsed ? "Expand" : "Collapse", collapsed ? expandIcon : collapseIcon);

            toggleButton.addActionListener(e -> {
                var nowCollapsed = collapsedSections.contains(targetProjectId);
                if (nowCollapsed) {
                    collapsedSections.remove(targetProjectId);
                    listWrapper.setVisible(true);
                    bar.setVisible(true);
                    toggleButton.setIcon(collapseIcon);
                    toggleButton.setToolTipText("Collapse");
                } else {
                    collapsedSections.add(targetProjectId);
                    listWrapper.setVisible(false);
                    bar.setVisible(false);
                    toggleButton.setIcon(expandIcon);
                    toggleButton.setToolTipText("Expand");
                }
                section.revalidate();
                section.repaint();
                var ancestor = SwingUtilities.getAncestorOfClass(JScrollPane.class, section);
                if (ancestor != null) ancestor.getParent().revalidate();
                else if (section.getParent() != null) section.getParent().revalidate();
            });

            section.add(buildSectionHeader(title, targetProjectId, sectionIndex, sectionCount, hasSubProjects, toggleButton), BorderLayout.NORTH);
        }

        section.add(listWrapper, BorderLayout.CENTER);
        section.add(bar,         BorderLayout.SOUTH);

        return section;
    }

    private JPanel buildAddActionBar(UUID targetProjectId, JList<NamNode> actionList, boolean showEditButton) {
        var targetName = workspace.getNode(targetProjectId).map(n -> n.getTitle()).orElse("this project");
        var addActionButton = UiHelper.iconButton("Add action",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/plus.svg")).derive(16, 16));
        addActionButton.setToolTipText("Add action to project " + targetName);
        addActionButton.addActionListener(e -> showAddActionDialog(targetProjectId));

        var bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        bar.add(addActionButton);

        JButton editButton = null;
        if (showEditButton) {
            editButton = UiHelper.iconButton("Edit project…",
                    new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/pencil.svg")).derive(16, 16));
            editButton.setToolTipText("Edit project: " + targetName);
            final var eid = targetProjectId;
            editButton.addActionListener(e ->
                    new ProjectDialog(SwingUtilities.getWindowAncestor(this), eid, workspace, service, this::rebuild)
                            .setVisible(true));

            var renameButton = UiHelper.iconButton("Rename",
                    new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/cursor-text.svg")).derive(16, 16));
            renameButton.setToolTipText("Rename: " + targetName);
            renameButton.addActionListener(e -> {
                var current = workspace.getNode(targetProjectId).map(n -> n.getTitle()).orElse("");
                var input = (String) JOptionPane.showInputDialog(parent, "Project name:", "Rename",
                        JOptionPane.PLAIN_MESSAGE, null, null, current);
                if (input == null || input.isBlank()) return;
                try { service.renameNode(targetProjectId, input.strip()); rebuild(); }
                catch (IOException ex) { showError(ex.getMessage()); }
            });

            var descButton = UiHelper.iconButton("Edit description",
                    new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/notes.svg")).derive(16, 16));
            descButton.setToolTipText("Edit description: " + targetName);
            descButton.addActionListener(e -> showDescriptionDialog(targetProjectId, targetName));

            bar.add(renameButton);
            bar.add(descButton);
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

        if (showEditButton) {
            var deleteButton = UiHelper.iconButton("Delete project",
                    new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/trash.svg")).derive(16, 16));
            deleteButton.setToolTipText("Delete project: " + targetName);
            deleteButton.addActionListener(e -> {
                var subtree  = workspace.collectSubtree(targetProjectId);
                var projects = (int) subtree.stream().skip(1)
                        .map(workspace::getNode).flatMap(java.util.Optional::stream)
                        .filter(n -> n.isProject()).count();
                var actions  = (int) subtree.stream().skip(1)
                        .map(workspace::getNode).flatMap(java.util.Optional::stream)
                        .filter(n -> !n.isProject()).count();
                String msg;
                if (projects == 0 && actions == 0) {
                    msg = "Delete \"" + targetName + "\"? This cannot be undone.";
                } else {
                    var parts = new java.util.ArrayList<String>();
                    if (projects > 0) parts.add(projects + " sub-project" + (projects > 1 ? "s" : ""));
                    if (actions  > 0) parts.add(actions  + " action"      + (actions  > 1 ? "s" : ""));
                    msg = "Delete \"" + targetName + "\"? This will also permanently remove "
                            + String.join(" and ", parts) + ".";
                }
                var confirm = JOptionPane.showConfirmDialog(parent, msg,
                        "Delete project", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) return;
                try {
                    service.deleteRecursive(targetProjectId);
                    rebuild();
                } catch (IOException ex) {
                    showError(ex.getMessage());
                }
            });
            bar.add(deleteButton);
        }

        if (editButton != null) bar.add(editButton);

        return bar;
    }

    private JComponent buildSectionHeader(String title, UUID navigateToId,
                                          int sectionIndex, int sectionCount, boolean hasSubProjects,
                                          JButton toggleButton) {
        var header = new JPanel(new BorderLayout());

        var orderButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        orderButtons.add(toggleButton);

        if (sectionIndex < 0) {
            var lbl = new JLabel(title);
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
            header.add(lbl, BorderLayout.CENTER);
        } else {
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

            orderButtons.add(upButton);
            orderButtons.add(downButton);
            header.add(btn, BorderLayout.CENTER);
        }

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

    private void showAddActionDialog(UUID targetProjectId) {
        var titleField = new JTextField(24);
        var panel = new JPanel(new BorderLayout(0, 4));
        panel.add(new JLabel("Action title:"), BorderLayout.NORTH);
        panel.add(titleField, BorderLayout.CENTER);

        var options = new Object[]{"Create & Edit", "Create", "Cancel"};
        var result  = JOptionPane.showOptionDialog(parent, panel, "Add action",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[1]);

        if (result == 2 || result < 0) return;
        var title = titleField.getText().strip();
        if (title.isBlank()) return;

        try {
            var newId = service.addChild(targetProjectId, title);
            rebuild();
            if (result == 0)
                new ActionDialog(parent, newId, workspace, service, true, this::rebuild).setVisible(true);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent, "Failed to save: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showDescriptionDialog(UUID projectId, String projectName) {
        var current = workspace.getNode(projectId).map(n -> n.getDescription()).orElse("");
        var area = new JTextArea(current != null ? current : "", 6, 40);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        var scroll = new JScrollPane(area);
        var result = JOptionPane.showConfirmDialog(parent, scroll,
                "Description: " + projectName, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        try { service.updateDescription(projectId, area.getText().strip()); rebuild(); }
        catch (IOException ex) { showError(ex.getMessage()); }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(parent, "Failed to save: " + message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void navigateTo(UUID projectId) {
        parentProjectId  = currentProjectId;
        currentProjectId = projectId;
        collapsedSections.clear();
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
