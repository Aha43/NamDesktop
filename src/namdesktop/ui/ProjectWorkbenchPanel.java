package namdesktop.ui;

import namdesktop.lens.ChildSection;
import namdesktop.lens.MissionControlLens;
import namdesktop.lens.MissionControlStation;
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
    private final String            backLabel;
    private final Runnable          onNavigateToProjects;
    private       UUID              currentProjectId;
    private       UUID              parentProjectId;
    private       UUID              pendingSelection;
    private final Set<UUID>         collapsedSections = new HashSet<>();
    private       boolean           accordionMode     = false;
    private       boolean           mcrMode           = false;
    private       UUID              mcrReturnId       = null;
    private       List<UUID>        currentSectionIds = List.of();

    public ProjectWorkbenchPanel(Window parent, NamWorkspace workspace,
                                  NamWorkspaceService service, UUID initialProjectId,
                                  Runnable onNavigateToProjects) {
        this(parent, workspace, service, initialProjectId, "Projects", onNavigateToProjects);
    }

    public ProjectWorkbenchPanel(Window parent, NamWorkspace workspace,
                                  NamWorkspaceService service, UUID initialProjectId,
                                  String backLabel, Runnable onNavigateToProjects) {
        super(new BorderLayout());
        this.parent               = parent;
        this.workspace            = workspace;
        this.service              = service;
        this.backLabel            = backLabel;
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
        currentSectionIds = List.copyOf(sectionIds);
        var hasSubProjects = !projection.childSections().isEmpty();
        if (mcrMode && !hasSubProjects) mcrMode = false;
        add(buildBreadcrumbBar(projection.breadcrumb(), sectionIds, hasSubProjects), BorderLayout.NORTH);
        if (mcrMode) add(new JScrollPane(buildMcrGrid()),         BorderLayout.CENTER);
        else         add(new JScrollPane(buildContent(projection)), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    // --- breadcrumb ---

    private JPanel buildBreadcrumbBar(List<NamNode> breadcrumb, List<UUID> sectionIds, boolean hasSubProjects) {
        var bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        var crumbs = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        crumbs.add(breadcrumbLink(backLabel, onNavigateToProjects));
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
        var accordionIcon   = new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/layout-list.svg")).derive(16, 16);
        var accordionButton = new JToggleButton(accordionIcon);
        accordionButton.setSelected(accordionMode);
        accordionButton.setToolTipText(accordionMode
                ? "Accordion mode on — opening a section closes others (click to turn off)"
                : "Accordion mode off — click to open one section at a time");
        accordionButton.addActionListener(e -> {
            accordionMode = accordionButton.isSelected();
            accordionButton.setToolTipText(accordionMode
                    ? "Accordion mode on — opening a section closes others (click to turn off)"
                    : "Accordion mode off — click to open one section at a time");
        });

        var mcrIcon   = new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/layout-dashboard.svg")).derive(16, 16);
        var mcrButton = new JToggleButton(mcrIcon);
        mcrButton.setSelected(mcrMode);
        mcrButton.setEnabled(hasSubProjects);
        mcrButton.setToolTipText(mcrMode ? "MCR view — click to return to workbench" : "Open sub-projects in MCR view");
        mcrButton.addActionListener(e -> { mcrMode = mcrButton.isSelected(); rebuild(); });

        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.add(mcrButton);
        if (!mcrMode && !sectionIds.isEmpty()) {
            buttons.add(accordionButton);
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
                if (nowCollapsed && accordionMode) {
                    collapsedSections.addAll(currentSectionIds);
                    collapsedSections.remove(targetProjectId);
                    rebuild();
                    return;
                }
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
        var addActionButton = UiHelper.iconButton("Add action",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/plus.svg")).derive(16, 16));
        var targetName = workspace.getNode(targetProjectId).map(n -> n.getTitle()).orElse("this project");
        addActionButton.setToolTipText("Add action to project " + targetName);
        addActionButton.addActionListener(e -> {
            var selected = actionList != null ? actionList.getSelectedValue() : null;
            showAddActionDialog(targetProjectId, selected != null ? selected.getId() : null);
        });

        var bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        bar.add(addActionButton);

        if (actionList != null) {
            var renameActionButton = UiHelper.iconButton("Rename action",
                    new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/cursor-text.svg")).derive(16, 16));
            renameActionButton.setToolTipText("Rename selected action");
            renameActionButton.setEnabled(false);
            renameActionButton.addActionListener(e -> {
                var node = actionList.getSelectedValue();
                if (node == null) return;
                var input = (String) JOptionPane.showInputDialog(parent, "Action name:", "Rename action",
                        JOptionPane.PLAIN_MESSAGE, null, null, node.getTitle());
                if (input == null || input.isBlank()) return;
                try { pendingSelection = node.getId(); service.renameNode(node.getId(), input.strip()); rebuild(); }
                catch (IOException ex) { showError(ex.getMessage()); }
            });

            var editTagsButton = UiHelper.iconButton("Edit action tags",
                    new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/tag.svg")).derive(16, 16));
            editTagsButton.setToolTipText("Edit tags of selected action");
            editTagsButton.setEnabled(false);
            editTagsButton.addActionListener(e -> {
                var node = actionList.getSelectedValue();
                if (node == null) return;
                var current = String.join(", ", node.getTags());
                var input = (String) JOptionPane.showInputDialog(parent, "Tags (comma-separated):", "Edit action tags",
                        JOptionPane.PLAIN_MESSAGE, null, null, current);
                if (input == null) return;
                var tags = java.util.Arrays.stream(input.split(","))
                        .map(String::strip).filter(s -> !s.isEmpty()).toList();
                try { pendingSelection = node.getId(); service.updateTags(node.getId(), tags); rebuild(); }
                catch (IOException ex) { showError(ex.getMessage()); }
            });

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
                var idx = actionList.getSelectedIndex();
                var size = actionList.getModel().getSize();
                renameActionButton.setEnabled(idx >= 0);
                editTagsButton.setEnabled(idx >= 0);
                upButton.setEnabled(idx > 0);
                downButton.setEnabled(idx >= 0 && idx < size - 1);
            });

            var restoredIdx = actionList.getSelectedIndex();
            renameActionButton.setEnabled(restoredIdx >= 0);
            editTagsButton.setEnabled(restoredIdx >= 0);
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

            bar.add(renameActionButton);
            bar.add(editTagsButton);
            bar.add(upButton);
            bar.add(downButton);
        }

        return bar;
    }

    private JComponent buildSectionHeader(String title, UUID navigateToId,
                                          int sectionIndex, int sectionCount, boolean hasSubProjects,
                                          JButton toggleButton) {
        var header = new JPanel(new BorderLayout());

        // LEFT: project operation buttons
        var projectName = workspace.getNode(navigateToId).map(n -> n.getTitle()).orElse("project");

        var renameBtn = UiHelper.iconButton("Rename project",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/cursor-text.svg")).derive(16, 16));
        renameBtn.setToolTipText("Rename project: " + projectName);
        renameBtn.addActionListener(e -> {
            var current = workspace.getNode(navigateToId).map(n -> n.getTitle()).orElse("");
            var input = (String) JOptionPane.showInputDialog(parent, "Project name:", "Rename project",
                    JOptionPane.PLAIN_MESSAGE, null, null, current);
            if (input == null || input.isBlank()) return;
            try { service.renameNode(navigateToId, input.strip()); rebuild(); }
            catch (IOException ex) { showError(ex.getMessage()); }
        });

        var descBtn = UiHelper.iconButton("Edit description",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/notes.svg")).derive(16, 16));
        descBtn.setToolTipText("Edit description: " + projectName);
        descBtn.addActionListener(e -> showDescriptionDialog(navigateToId, projectName));

        var deleteBtn = UiHelper.iconButton("Delete project",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/trash.svg")).derive(16, 16));
        deleteBtn.setToolTipText("Delete project: " + projectName);
        deleteBtn.addActionListener(e -> {
            var subtree  = workspace.collectSubtree(navigateToId);
            var projects = (int) subtree.stream().skip(1)
                    .map(workspace::getNode).flatMap(java.util.Optional::stream)
                    .filter(n -> n.isProject()).count();
            var actions  = (int) subtree.stream().skip(1)
                    .map(workspace::getNode).flatMap(java.util.Optional::stream)
                    .filter(n -> !n.isProject()).count();
            String msg;
            if (projects == 0 && actions == 0) {
                msg = "Delete \"" + projectName + "\"? This cannot be undone.";
            } else {
                var parts = new java.util.ArrayList<String>();
                if (projects > 0) parts.add(projects + " sub-project" + (projects > 1 ? "s" : ""));
                if (actions  > 0) parts.add(actions  + " action"      + (actions  > 1 ? "s" : ""));
                msg = "Delete \"" + projectName + "\"? This will also permanently remove "
                        + String.join(" and ", parts) + ".";
            }
            var confirm = JOptionPane.showConfirmDialog(parent, msg,
                    "Delete project", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
            try { service.deleteRecursive(navigateToId); rebuild(); }
            catch (IOException ex) { showError(ex.getMessage()); }
        });

        var pencilBtn = UiHelper.iconButton("Edit project…",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/pencil.svg")).derive(16, 16));
        pencilBtn.setToolTipText("Edit project: " + projectName);
        pencilBtn.addActionListener(e ->
                new ProjectDialog(parent, navigateToId, workspace, service, this::rebuild).setVisible(true));

        var projectButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        projectButtons.add(renameBtn);
        projectButtons.add(descBtn);
        projectButtons.add(deleteBtn);
        projectButtons.add(pencilBtn);

        // RIGHT: toggle + section-move buttons — always same set so CENTER stays aligned
        var upButton = UiHelper.iconButton("Move section up",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/arrow-up.svg")).derive(16, 16));
        var downButton = UiHelper.iconButton("Move section down",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/arrow-down.svg")).derive(16, 16));

        var orderBox = Box.createHorizontalBox();
        orderBox.add(toggleButton);
        orderBox.add(Box.createHorizontalStrut(2));
        orderBox.add(upButton);
        orderBox.add(Box.createHorizontalStrut(2));
        orderBox.add(downButton);

        if (sectionIndex < 0) {
            for (var b : new JButton[]{upButton, downButton}) {
                b.setIcon(BLANK_ICON);
                b.setBorderPainted(false);
                b.setContentAreaFilled(false);
                b.setFocusable(false);
                b.setEnabled(false);
            }
            var lbl = new JLabel("<html><i>This project</i></html>", SwingConstants.CENTER);
            header.add(lbl, BorderLayout.CENTER);
        } else {
            var btn = new JButton(title + " ›");
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            var style = Font.BOLD | (hasSubProjects ? Font.ITALIC : Font.PLAIN);
            btn.setFont(btn.getFont().deriveFont((float) btn.getFont().getSize()).deriveFont(style));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> navigateTo(navigateToId));

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

            header.add(btn, BorderLayout.CENTER);
        }

        header.add(projectButtons, BorderLayout.WEST);
        header.add(orderBox,       BorderLayout.EAST);
        return header;
    }

    private static final int BADGE_WIDTH = 28;

    private JList<NamNode> buildActionList(List<NamNode> actions, UUID targetProjectId) {
        var model = new DefaultListModel<NamNode>();
        for (var a : actions) model.addElement(a);

        var list = new JList<>(model) {
            @Override
            public String getToolTipText(MouseEvent e) {
                var idx = locationToIndex(e.getPoint());
                if (idx < 0) return null;
                var bounds = getCellBounds(idx, idx);
                if (bounds != null && e.getX() < bounds.x + BADGE_WIDTH) return "Set status";
                var desc = model.getElementAt(idx).getDescription();
                if (desc == null || desc.isBlank()) return null;
                return desc.length() <= 100 ? desc : desc.substring(0, 100) + "…";
            }
        };
        list.setCellRenderer(new ActionCellRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        final int[] lastSelected = {-1};
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastSelected[0] = list.getSelectedIndex();
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                var idx = list.locationToIndex(e.getPoint());
                if (idx < 0) return;
                var bounds = list.getCellBounds(idx, idx);
                // Badge zone → status popup
                if (bounds != null && e.getClickCount() == 1 && e.getX() < bounds.x + BADGE_WIDTH) {
                    showStatusPopup(list, model.getElementAt(idx), e.getX(), e.getY());
                    return;
                }
                // Double-click → open full dialog
                if (e.getClickCount() == 2) {
                    var node = model.getElementAt(idx);
                    new ActionDialog(parent, node.getId(), workspace, service, true, ProjectWorkbenchPanel.this::rebuild)
                            .setVisible(true);
                    return;
                }
                // Single-click on already-selected title → inline rename
                if (e.getClickCount() == 1 && idx == lastSelected[0] && bounds != null
                        && e.getX() >= bounds.x + BADGE_WIDTH) {
                    startInlineRename(list, model, idx);
                }
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

    private void showAddActionDialog(UUID targetProjectId, UUID beforeId) {
        var titleField = new JTextField(24);
        titleField.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && titleField.isShowing())
                SwingUtilities.invokeLater(titleField::requestFocusInWindow);
        });
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
            var newId = beforeId != null
                    ? service.insertChildBefore(targetProjectId, beforeId, title)
                    : service.addChild(targetProjectId, title);
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

    private void startInlineRename(JList<NamNode> list, DefaultListModel<NamNode> model, int idx) {
        var node   = model.getElementAt(idx);
        var bounds = list.getCellBounds(idx, idx);
        if (bounds == null) return;

        var field = new JTextField(node.getTitle());
        int fx = bounds.x + BADGE_WIDTH + 4;
        field.setBounds(fx, bounds.y + 1, bounds.width - fx - 2, bounds.height - 2);
        field.selectAll();
        list.setLayout(null);
        list.add(field);
        list.revalidate();
        field.requestFocusInWindow();

        Runnable commit = () -> {
            var text = field.getText().strip();
            list.remove(field);
            list.setLayout(null);
            list.revalidate();
            list.repaint();
            if (!text.isBlank() && !text.equals(node.getTitle())) {
                try { pendingSelection = node.getId(); service.renameNode(node.getId(), text); rebuild(); }
                catch (IOException ex) { showError(ex.getMessage()); }
            }
        };
        Runnable cancel = () -> {
            list.remove(field);
            list.setLayout(null);
            list.revalidate();
            list.repaint();
        };

        field.addActionListener(e -> commit.run());
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { commit.run(); }
        });
        field.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) cancel.run();
            }
        });
    }

    private void showStatusPopup(JComponent source, NamNode node, int x, int y) {
        var current = node.getStatus();
        var menu = new JPopupMenu();
        for (var status : new NodeStatus[]{NodeStatus.NEXT, NodeStatus.BACKLOG, NodeStatus.DONE}) {
            var label = switch (status) {
                case NEXT    -> "Next";
                case BACKLOG -> "Backlog";
                case DONE    -> "Done";
                default      -> status.name();
            };
            var item = new JMenuItem((current == status ? "✓ " : "   ") + label);
            item.setEnabled(current != status);
            final var s = status;
            item.addActionListener(e -> setStatus(node, s));
            menu.add(item);
        }
        menu.show(source, x, y);
    }

    private void setStatus(NamNode node, NodeStatus status) {
        try {
            switch (status) {
                case NEXT    -> service.markNext(node.getId());
                case BACKLOG -> service.markBacklog(node.getId());
                case DONE    -> service.markDone(node.getId());
                default      -> {}
            }
            pendingSelection = node.getId();
            rebuild();
        } catch (IOException ex) { showError(ex.getMessage()); }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(parent, "Failed to save: " + message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void navigateTo(UUID projectId) {
        if (mcrMode) mcrReturnId = currentProjectId;
        mcrMode          = projectId.equals(mcrReturnId);
        if (mcrMode) mcrReturnId = null;
        parentProjectId  = currentProjectId;
        currentProjectId = projectId;
        collapsedSections.clear();
        rebuild();
    }

    // --- MCR view ---

    private static final javax.swing.Icon BLANK_ICON = new javax.swing.Icon() {
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {}
        public int getIconWidth()  { return 16; }
        public int getIconHeight() { return 16; }
    };

    private static final Color MCR_RED   = new Color(180,  60,  60);
    private static final Color MCR_AMBER = new Color(190, 130,   0);
    private static final Color MCR_GREEN = new Color( 50, 150,  50);

    private JPanel buildMcrGrid() {
        var stations = new MissionControlLens().stations(currentProjectId, workspace);
        var grid = new JPanel(new WrapLayout(FlowLayout.LEFT, 12, 12));
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        if (stations.isEmpty()) {
            var lbl = new JLabel("No sub-projects found.", SwingConstants.CENTER);
            lbl.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
            grid.add(lbl);
        } else {
            for (var s : stations) grid.add(buildMcrCard(s));
        }
        return grid;
    }

    private JPanel buildMcrCard(MissionControlStation s) {
        var card = new JPanel(new BorderLayout(0, 6));
        card.setPreferredSize(new Dimension(200, 150));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(mcrHeatColor(s.doneRatio()), 3),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        var title = new JLabel("<html><center>" + s.title() + "</center></html>", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));

        var stats = new JPanel();
        stats.setLayout(new BoxLayout(stats, BoxLayout.PAGE_AXIS));
        stats.setOpaque(false);
        stats.add(mcrStat(s.subProjectCount() + " sub-project" + (s.subProjectCount() != 1 ? "s" : "")));
        stats.add(mcrStat("Max depth: " + s.maxDepth()));
        stats.add(mcrStat("Done: " + s.doneCount() + " / " + s.totalActions()));

        card.add(title, BorderLayout.NORTH);
        card.add(stats, BorderLayout.CENTER);
        addMcrClickHandler(card, () -> navigateTo(s.id()));
        return card;
    }

    private void addMcrClickHandler(java.awt.Component c, Runnable onClick) {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (c instanceof JComponent jc) jc.setToolTipText("Open project workbench");
        c.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { onClick.run(); }
        });
        if (c instanceof Container container) {
            for (var child : container.getComponents()) addMcrClickHandler(child, onClick);
        }
    }

    private JLabel mcrStat(String text) {
        var label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(12f));
        label.setAlignmentX(CENTER_ALIGNMENT);
        return label;
    }

    private Color mcrHeatColor(double ratio) {
        if (ratio >= 0.67) return MCR_GREEN;
        if (ratio >= 0.33) return MCR_AMBER;
        return MCR_RED;
    }

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

    private static final Color BADGE_NEXT    = new Color( 50, 150,  80);
    private static final Color BADGE_BACKLOG = new Color(160, 120,  30);
    private static final Color BADGE_DONE    = new Color(110, 110, 110);

    private static final class ActionCellRenderer implements ListCellRenderer<NamNode> {
        private final JPanel panel = new JPanel(new BorderLayout(6, 0));
        private final JLabel badge = new JLabel("", SwingConstants.CENTER);
        private final JLabel label = new JLabel();

        ActionCellRenderer() {
            badge.setOpaque(true);
            badge.setForeground(Color.WHITE);
            badge.setFont(badge.getFont().deriveFont(Font.BOLD, 10f));
            badge.setPreferredSize(new Dimension(BADGE_WIDTH, 0));
            badge.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
            panel.add(badge, BorderLayout.WEST);
            panel.add(label, BorderLayout.CENTER);
            panel.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends NamNode> list, NamNode node, int index, boolean isSelected, boolean cellHasFocus) {
            var status = node.getStatus();
            badge.setText(switch (status) {
                case NEXT    -> "N";
                case BACKLOG -> "B";
                case DONE    -> "D";
                default      -> "?";
            });
            var bg = isSelected ? list.getSelectionBackground() : list.getBackground();
            panel.setBackground(bg);
            badge.setBackground(bg);
            badge.setForeground(isSelected ? list.getSelectionForeground() : switch (status) {
                case NEXT    -> BADGE_NEXT;
                case BACKLOG -> BADGE_BACKLOG;
                default      -> BADGE_DONE;
            });
            label.setText(node.getTitle());
            label.setForeground(isSelected ? list.getSelectionForeground()
                                           : status == NodeStatus.DONE ? Color.GRAY : list.getForeground());
            return panel;
        }
    }
}
