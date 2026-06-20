package namdesktop.ui;

import namdesktop.app.AppSettings;
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
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class ProjectWorkbenchPanel extends JPanel {

    private final Window            parent;
    private final NamWorkspace      workspace;
    private final NamWorkspaceService service;
    private final String            backLabel;
    private final Runnable          onNavigateToProjects;
    private       Consumer<UUID>    onInternalNavigate  = id -> {};
    private       UUID              currentProjectId;
    private       UUID              parentProjectId;
    private       UUID              pendingSelection;
    private final Set<UUID>         collapsedSections = new HashSet<>();
    private final ColumnTransferHandler columnDnD     = new ColumnTransferHandler();
    private       boolean           accordionMode     = false;
    private       boolean           mcrMode           = false;
    private       boolean           columnMode        = false;
    private       LaneMode          laneMode          = LaneMode.ACTIONS;
    private       List<UUID>        currentSectionIds = List.of();

    /** Which lanes each Column-view column shows. */
    private enum LaneMode { ACTIONS, BOTH, PROJECTS }

    // Per-project workbench view, persisted across sessions in AppSettings: each project reopens
    // in the view (Workbench / Columns+lanes / Readiness) and with the columns collapsed as it
    // was left — including on startup session-restore.
    private enum WbMode { WORKBENCH, COLUMN, MCR }
    private record WbView(WbMode mode, LaneMode lane) {}
    private static final WbView DEFAULT_VIEW = new WbView(WbMode.WORKBENCH, LaneMode.ACTIONS);

    private void persistSettings() {
        var s = AppSettings.getInstance();
        if (s == null) return;
        try { s.save(); } catch (IOException ignored) {}
    }

    private boolean isColumnCollapsed(UUID columnId) {
        var s = AppSettings.getInstance();
        if (s == null) return false;
        var list = s.getCollapsedColumns().get(currentProjectId.toString());
        return list != null && list.contains(columnId.toString());
    }

    private void setColumnCollapsed(UUID columnId, boolean collapsed) {
        var s = AppSettings.getInstance();
        if (s != null) {
            var map = s.getCollapsedColumns();
            var key = currentProjectId.toString();
            var id  = columnId.toString();
            var list = new java.util.ArrayList<>(map.getOrDefault(key, List.of()));
            if (collapsed) { if (!list.contains(id)) list.add(id); }
            else           { list.remove(id); }
            if (list.isEmpty()) map.remove(key); else map.put(key, list);
            persistSettings();
        }
        rebuild();
    }

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
        applyView(initialProjectId);
        rebuild();
    }

    /** Restores the persisted view for {@code projectId} into the mode flags. */
    private void applyView(UUID projectId) {
        var s    = AppSettings.getInstance();
        var view = decodeView(s == null ? null : s.getProjectViews().get(projectId.toString()));
        columnMode = view.mode() == WbMode.COLUMN;
        mcrMode    = view.mode() == WbMode.MCR;
        laneMode   = view.lane();
    }

    /** Records the current view for {@code currentProjectId} so it reopens the same way next time. */
    private void rememberView() {
        var s = AppSettings.getInstance();
        if (s == null) return;
        var mode = columnMode ? WbMode.COLUMN : mcrMode ? WbMode.MCR : WbMode.WORKBENCH;
        s.getProjectViews().put(currentProjectId.toString(), mode.name() + ":" + laneMode.name());
        persistSettings();
    }

    private static WbView decodeView(String enc) {
        if (enc == null) return DEFAULT_VIEW;
        var parts = enc.split(":");
        try {
            var mode = WbMode.valueOf(parts[0]);
            var lane = parts.length > 1 ? LaneMode.valueOf(parts[1]) : LaneMode.ACTIONS;
            return new WbView(mode, lane);
        } catch (RuntimeException e) {
            return DEFAULT_VIEW;
        }
    }

    public void refresh() { rebuild(); }

    /** The project's own direct actions that are still open (not sub-projects, not done). */
    static List<NamNode> focusableDirectActions(NamWorkspace workspace, UUID projectId) {
        return workspace.getChildren(projectId).stream()
                .filter(n -> !n.isProject())
                .filter(n -> n.getStatus() != NodeStatus.DONE)
                .toList();
    }

    /** Opens the focus deck over the current project's open direct actions. */
    private void enterFocusMode() {
        var actions = focusableDirectActions(workspace, currentProjectId);
        if (actions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No open actions to focus on.",
                    "Focus mode", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        var projectName = workspace.getNode(currentProjectId).map(NamNode::getTitle).orElse(null);
        var cards = new java.util.ArrayList<MoonCardPanel.Card>();
        for (var a : actions)
            cards.add(new MoonCardPanel.Card(a.getId(), a.getTitle(), a.getDescription(), projectName));
        removeAll();
        add(new MoonCardPanel(cards, service, this::exitFocusMode), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void exitFocusMode() { rebuild(); }

    /** Called with the new project ID on every internal breadcrumb/card navigation. */
    public void setOnInternalNavigate(Consumer<UUID> cb) {
        onInternalNavigate = cb != null ? cb : id -> {};
    }

    /** Repositions the panel to show a specific project without firing onInternalNavigate. */
    public void showProject(UUID projectId) {
        parentProjectId  = workspace.getParent(projectId).map(n -> n.getId()).orElse(null);
        currentProjectId = projectId;
        collapsedSections.clear();
        applyView(projectId);
        rebuild();
    }

    public void triggerAdd() { showAddActionDialog(currentProjectId, null); }

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
        if (!hasSubProjects && (mcrMode || columnMode)) {
            mcrMode = columnMode = false;
            laneMode = LaneMode.ACTIONS;
            rememberView();  // a project that lost its sub-projects falls back to the workbench
        }
        add(buildBreadcrumbBar(projection.breadcrumb(), sectionIds, hasSubProjects), BorderLayout.NORTH);
        if (columnMode)   add(new JScrollPane(buildColumnView(projection)), BorderLayout.CENTER);
        else if (mcrMode) add(new JScrollPane(buildMcrGrid()),              BorderLayout.CENTER);
        else              add(new JScrollPane(buildContent(projection)),    BorderLayout.CENTER);
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
                if (namdesktop.app.AppSettings.getInstance().isPowerMode()) {
                    var ancestorEdit = UiHelper.iconOnlyButton("Edit project: " + node.getTitle(),
                            new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/pencil.svg")).derive(14, 14));
                    ancestorEdit.addActionListener(e ->
                            new ProjectDialog(parent, id, workspace, service, this::rebuild).setVisible(true));
                    crumbs.add(ancestorEdit);
                }
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

        // View-mode trio — Workbench (default) / Columns / Goal — mutually exclusive, so there is
        // always an explicit way back to the workbench (the remembered view no longer resets it).
        var wbIcon   = new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/list-details.svg")).derive(16, 16);
        var wbButton = new JToggleButton(wbIcon);
        wbButton.setSelected(!columnMode && !mcrMode);
        wbButton.setToolTipText("Workbench — the default stacked view");
        wbButton.addActionListener(e -> { columnMode = false; mcrMode = false; rememberView(); rebuild(); });

        var columnIcon   = new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/layout-columns.svg")).derive(16, 16);
        var columnButton = new JToggleButton(columnIcon);
        columnButton.setSelected(columnMode);
        columnButton.setEnabled(hasSubProjects);
        columnButton.setToolTipText("Column view — sub-projects as columns");
        columnButton.addActionListener(e -> { columnMode = true; mcrMode = false; rememberView(); rebuild(); });

        var mcrIcon   = new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/layout-dashboard.svg")).derive(16, 16);
        var mcrButton = new JToggleButton(mcrIcon);
        mcrButton.setSelected(mcrMode);
        mcrButton.setEnabled(hasSubProjects);
        mcrButton.setToolTipText("Readiness view — sub-project progress board");
        mcrButton.addActionListener(e -> { mcrMode = true; columnMode = false; rememberView(); rebuild(); });

        var modeGroup = new ButtonGroup();
        modeGroup.add(wbButton);
        modeGroup.add(columnButton);
        modeGroup.add(mcrButton);

        // Column-view lane control — cycles actions-only → both → sub-projects-only.
        var laneButton = UiHelper.iconButton(laneTooltip(laneMode), laneIcon(laneMode));
        laneButton.setToolTipText(laneTooltip(laneMode));
        laneButton.addActionListener(e -> { laneMode = nextLane(laneMode); rememberView(); rebuild(); });

        // Each view mode sits with its own controls; controls stay in place (disabled when the
        // mode is inactive) so the toolbar never reflows as you switch views.
        var workbenchMode = !columnMode && !mcrMode;
        accordionButton.setEnabled(workbenchMode);
        toggleAllButton.setEnabled(workbenchMode);
        laneButton.setEnabled(columnMode);

        var focusButton = UiHelper.iconButton("Focus mode",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/stack-2.svg")).derive(16, 16));
        focusButton.setToolTipText("Work through this project's actions one at a time");
        focusButton.setEnabled(!focusableDirectActions(workspace, currentProjectId).isEmpty());
        focusButton.addActionListener(e -> enterFocusMode());

        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.add(wbButton);
        buttons.add(accordionButton);
        buttons.add(toggleAllButton);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(columnButton);
        buttons.add(laneButton);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(mcrButton);
        var sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 16));
        buttons.add(sep);
        buttons.add(focusButton);
        buttons.add(newProjectButton);
        if (namdesktop.app.AppSettings.getInstance().isPowerMode()) {
            for (var b : buildProjectPowerButtons(currentProjectId, projectName))
                buttons.add(b);
        }
        buttons.add(editButton);

        bar.add(crumbs,   BorderLayout.CENTER);
        bar.add(buttons,  BorderLayout.EAST);
        return bar;
    }

    private static LaneMode nextLane(LaneMode m) {
        return switch (m) {
            case ACTIONS  -> LaneMode.BOTH;
            case BOTH     -> LaneMode.PROJECTS;
            case PROJECTS -> LaneMode.ACTIONS;
        };
    }

    private static FlatSVGIcon laneIcon(LaneMode m) {
        var name = switch (m) {
            case ACTIONS  -> "list-check";
            case BOTH     -> "layout-rows";
            case PROJECTS -> "folders";
        };
        return new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/" + name + ".svg")).derive(16, 16);
    }

    private static String laneTooltip(LaneMode m) {
        return switch (m) {
            case ACTIONS  -> "Lanes: actions only — click to also show sub-projects";
            case BOTH     -> "Lanes: actions and sub-projects — click to show sub-projects only";
            case PROJECTS -> "Lanes: sub-projects only — click to show actions only";
        };
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
            var power = namdesktop.app.AppSettings.getInstance().isPowerMode();

            var renameActionButton = power ? UiHelper.iconButton("Rename action",
                    new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/cursor-text.svg")).derive(16, 16)) : null;
            var editTagsButton = power ? UiHelper.iconButton("Edit action tags",
                    new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/tag.svg")).derive(16, 16)) : null;

            if (renameActionButton != null) {
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
            }
            if (editTagsButton != null) {
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
            }

            var editActionButton = UiHelper.iconButton("Edit action…",
                    new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/pencil.svg")).derive(16, 16));
            editActionButton.setToolTipText("Edit selected action");
            editActionButton.setEnabled(false);
            editActionButton.addActionListener(e -> {
                var node = actionList.getSelectedValue();
                if (node == null) return;
                new ActionDialog(parent, node.getId(), workspace, service, true,
                        ProjectWorkbenchPanel.this::rebuild).setVisible(true);
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
                if (renameActionButton != null) renameActionButton.setEnabled(idx >= 0);
                if (editTagsButton    != null) editTagsButton.setEnabled(idx >= 0);
                editActionButton.setEnabled(idx >= 0);
                upButton.setEnabled(idx > 0);
                downButton.setEnabled(idx >= 0 && idx < size - 1);
            });

            var restoredIdx = actionList.getSelectedIndex();
            if (renameActionButton != null) renameActionButton.setEnabled(restoredIdx >= 0);
            if (editTagsButton    != null) editTagsButton.setEnabled(restoredIdx >= 0);
            editActionButton.setEnabled(restoredIdx >= 0);
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

            if (renameActionButton != null) bar.add(renameActionButton);
            if (editTagsButton    != null) bar.add(editTagsButton);
            bar.add(editActionButton);
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

        var pencilBtn = UiHelper.iconButton("Edit project…",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/pencil.svg")).derive(16, 16));
        pencilBtn.setToolTipText("Edit project: " + projectName);
        pencilBtn.addActionListener(e ->
                new ProjectDialog(parent, navigateToId, workspace, service, this::rebuild).setVisible(true));

        var projectButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        if (sectionIndex >= 0) {
            if (namdesktop.app.AppSettings.getInstance().isPowerMode()) {
                for (var b : buildProjectPowerButtons(navigateToId, projectName))
                    projectButtons.add(b);
            }
            projectButtons.add(pencilBtn);
        }

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

    private List<JButton> buildProjectPowerButtons(UUID id, String name) {
        var renameBtn = UiHelper.iconButton("Rename project",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/cursor-text.svg")).derive(16, 16));
        renameBtn.setToolTipText("Rename project: " + name);
        renameBtn.addActionListener(e -> {
            var current = workspace.getNode(id).map(n -> n.getTitle()).orElse("");
            var input = (String) JOptionPane.showInputDialog(parent, "Project name:", "Rename project",
                    JOptionPane.PLAIN_MESSAGE, null, null, current);
            if (input == null || input.isBlank()) return;
            try { service.renameNode(id, input.strip()); rebuild(); }
            catch (IOException ex) { showError(ex.getMessage()); }
        });
        var descBtn = UiHelper.iconButton("Edit description",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/notes.svg")).derive(16, 16));
        descBtn.setToolTipText("Edit description: " + name);
        descBtn.addActionListener(e -> showDescriptionDialog(id, name));
        var deleteBtn = UiHelper.iconButton("Delete project",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/trash.svg")).derive(16, 16));
        deleteBtn.setToolTipText("Delete project: " + name);
        deleteBtn.addActionListener(e -> {
            var subtree  = workspace.collectSubtree(id);
            var projects = (int) subtree.stream().skip(1)
                    .map(workspace::getNode).flatMap(java.util.Optional::stream)
                    .filter(n -> n.isProject()).count();
            var actions  = (int) subtree.stream().skip(1)
                    .map(workspace::getNode).flatMap(java.util.Optional::stream)
                    .filter(n -> !n.isProject()).count();
            String msg;
            if (projects == 0 && actions == 0) {
                msg = "Delete \"" + name + "\"? This cannot be undone.";
            } else {
                var parts = new java.util.ArrayList<String>();
                if (projects > 0) parts.add(projects + " sub-project" + (projects > 1 ? "s" : ""));
                if (actions  > 0) parts.add(actions  + " action"      + (actions  > 1 ? "s" : ""));
                msg = "Delete \"" + name + "\"? This will also permanently remove "
                        + String.join(" and ", parts) + ".";
            }
            if (JOptionPane.showConfirmDialog(parent, msg, "Delete project",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
            try { service.deleteRecursive(id); rebuild(); }
            catch (IOException ex) { showError(ex.getMessage()); }
        });
        return List.of(renameBtn, descBtn, deleteBtn);
    }

    private static final int BADGE_WIDTH = 36;

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
                if (bounds != null && e.getX() >= bounds.x + bounds.width - PENCIL_WIDTH)
                    return "Edit: " + model.getElementAt(idx).getTitle();
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
                // Pencil zone → open full dialog (single click, no prior selection required)
                if (bounds != null && e.getX() >= bounds.x + bounds.width - PENCIL_WIDTH) {
                    var node = model.getElementAt(idx);
                    new ActionDialog(parent, node.getId(), workspace, service, true, ProjectWorkbenchPanel.this::rebuild)
                            .setVisible(true);
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
                        && e.getX() >= bounds.x + BADGE_WIDTH
                        && AppSettings.getInstance().isClickToRename()) {
                    startInlineRename(list, model, idx);
                }
            }
        });

        list.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "openDialog");
        list.getActionMap().put("openDialog", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent ev) {
                var idx = list.getSelectedIndex();
                if (idx < 0) return;
                var node = model.getElementAt(idx);
                new ActionDialog(parent, node.getId(), workspace, service, true,
                        ProjectWorkbenchPanel.this::rebuild).setVisible(true);
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
        var area = new JTextArea(5, 24);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && area.isShowing())
                SwingUtilities.invokeLater(area::requestFocusInWindow);
        });
        var panel = new JPanel(new BorderLayout(0, 4));
        panel.add(new JLabel("Action title(s) — one per line:"), BorderLayout.NORTH);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);

        var options = new Object[]{"Create & Edit", "Create", "Cancel"};
        var choice  = JOptionPane.showOptionDialog(parent, panel, "Add action",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[1]);

        if (choice == 2 || choice < 0) return;
        var lines = area.getText().lines().map(String::strip).filter(s -> !s.isBlank()).toList();
        if (lines.isEmpty()) return;

        try {
            UUID lastId = null;
            UUID insertBefore = beforeId;
            // When inserting before a selected item, reverse so typed order is preserved
            var ordered = new java.util.ArrayList<>(lines);
            if (insertBefore != null) java.util.Collections.reverse(ordered);
            for (var line : ordered) {
                if (insertBefore != null) {
                    lastId = service.insertChildBefore(targetProjectId, insertBefore, line);
                    insertBefore = lastId;
                } else {
                    lastId = service.addChild(targetProjectId, line);
                }
            }
            rebuild();
            if (choice == 0 && lines.size() == 1 && lastId != null)
                new ActionDialog(parent, lastId, workspace, service, true, this::rebuild).setVisible(true);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent, "Failed to save: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
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
            var label = switch (status) { case NEXT -> "Next"; case BACKLOG -> "Backlog"; default -> "Done"; };
            var item  = new JMenuItem((current == status ? "✓ " : "  ") + label);
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

    private void showDescriptionDialog(UUID projectId, String projectName) {
        var current = workspace.getNode(projectId).map(n -> n.getDescription()).orElse("");
        var area = new JTextArea(current != null ? current : "", 6, 40);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        var result = JOptionPane.showConfirmDialog(parent, new JScrollPane(area),
                "Description: " + projectName, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        try { service.updateDescription(projectId, area.getText().strip()); rebuild(); }
        catch (IOException ex) { showError(ex.getMessage()); }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(parent, "Failed to save: " + message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void navigateTo(UUID projectId) {
        onInternalNavigate.accept(projectId);
        parentProjectId  = currentProjectId;
        currentProjectId = projectId;
        collapsedSections.clear();
        applyView(projectId);  // each project reopens in its own remembered view this session
        rebuild();
    }

    // --- Column view ---

    private static final int COLUMN_WIDTH = 240;

    /**
     * Renders the current project's immediate child projects as columns and their direct
     * actions as cards. A leading "Unsorted" column holds the parent's own direct actions
     * (omitted when there are none). Moving a card to another column reparents the action
     * between sibling projects via {@link NamWorkspaceService#moveNode}.
     */
    private JComponent buildColumnView(WorkbenchProjection projection) {
        // Column id -> display label, in display order; drives the per-card move menu.
        var columns     = new java.util.LinkedHashMap<UUID, String>();
        var hasUnsorted = !projection.directActions().isEmpty();
        if (hasUnsorted) columns.put(currentProjectId, "Unsorted");
        for (var section : projection.childSections())
            columns.put(section.project().getId(), section.project().getTitle());

        var row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        var sections = projection.childSections();
        if (hasUnsorted) {
            row.add(buildColumn(null, currentProjectId, projection.directActions(), columns, -1, sections.size()));
            row.add(Box.createHorizontalStrut(12));
        }
        for (int i = 0; i < sections.size(); i++) {
            var s = sections.get(i);
            row.add(buildColumn(s.project(), s.project().getId(), s.directActions(), columns, i, sections.size()));
            if (i < sections.size() - 1) row.add(Box.createHorizontalStrut(12));
        }
        return row;
    }

    private JComponent buildColumn(NamNode project, UUID columnId, List<NamNode> actions,
                                   java.util.LinkedHashMap<UUID, String> columns,
                                   int sectionIndex, int sectionCount) {
        if (isColumnCollapsed(columnId)) {
            return buildCollapsedColumn(project, columnId, actions.size());
        }
        var column = new JPanel(new BorderLayout(0, 6)) {
            @Override public Dimension getPreferredSize() { return new Dimension(COLUMN_WIDTH, super.getPreferredSize().height); }
            @Override public Dimension getMaximumSize()   { return new Dimension(COLUMN_WIDTH, super.getMaximumSize().height); }
            @Override public Dimension getMinimumSize()   { return new Dimension(COLUMN_WIDTH, super.getMinimumSize().height); }
        };
        column.setAlignmentY(Component.TOP_ALIGNMENT);
        column.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        // Header — clickable project title, or a static "Unsorted" label.
        JComponent header;
        if (project == null) {
            var lbl = new JLabel("Unsorted");
            lbl.setToolTipText("Actions directly under this project (not in a sub-project)");
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD | Font.ITALIC));
            lbl.setForeground(UIManager.getColor("Label.disabledForeground"));
            header = lbl;
        } else {
            var btn = new JButton(project.getTitle() + " ›");
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setHorizontalAlignment(SwingConstants.LEFT);
            btn.setMargin(new Insets(0, 0, 0, 0));
            btn.setFont(btn.getFont().deriveFont(Font.BOLD));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.setToolTipText("Open " + project.getTitle());
            final var id = project.getId();
            btn.addActionListener(e -> navigateTo(id));
            header = btn;
        }

        var showActions  = laneMode != LaneMode.PROJECTS;
        var showProjects = laneMode != LaneMode.ACTIONS;
        var subProjectCount = (int) workspace.getChildren(columnId).stream().filter(NamNode::isProject).count();
        var countText = laneMode == LaneMode.PROJECTS
                ? subProjectCount + (subProjectCount == 1 ? " sub-project" : " sub-projects")
                : actions.size() + (actions.size() == 1 ? " action" : " actions");
        var count = new JLabel(countText);
        count.setForeground(UIManager.getColor("Label.disabledForeground"));
        count.setFont(count.getFont().deriveFont(11f));

        var titleBlock = new JPanel(new BorderLayout());
        titleBlock.add(header, BorderLayout.CENTER);
        titleBlock.add(count,  BorderLayout.SOUTH);

        // Header bar: collapse chevron (left) · title + count (center) · reorder arrows (right).
        var collapseBtn = UiHelper.iconOnlyButton("Collapse column",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/chevron-left.svg")).derive(14, 14));
        collapseBtn.addActionListener(e -> setColumnCollapsed(columnId, true));

        var head = new JPanel(new BorderLayout(4, 0));
        head.add(collapseBtn, BorderLayout.WEST);
        head.add(titleBlock,  BorderLayout.CENTER);

        if (sectionIndex >= 0) {  // real child-project columns can be reordered (Unsorted stays pinned)
            var leftBtn  = UiHelper.iconOnlyButton("Move column left",
                    new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/arrow-left.svg")).derive(14, 14));
            var rightBtn = UiHelper.iconOnlyButton("Move column right",
                    new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/arrow-right.svg")).derive(14, 14));
            leftBtn.setEnabled(sectionIndex > 0);
            rightBtn.setEnabled(sectionIndex < sectionCount - 1);
            leftBtn.addActionListener(e -> {
                try { service.moveProjectUp(currentProjectId, columnId); rebuild(); }
                catch (IOException ex) { showError(ex.getMessage()); }
            });
            rightBtn.addActionListener(e -> {
                try { service.moveProjectDown(currentProjectId, columnId); rebuild(); }
                catch (IOException ex) { showError(ex.getMessage()); }
            });
            var reorder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            reorder.setOpaque(false);
            reorder.add(leftBtn);
            reorder.add(rightBtn);
            head.add(reorder, BorderLayout.EAST);
        }

        // Body — actions lane and/or sub-projects lane, depending on the lane mode.
        JList<NamNode> actionsList = null;
        JComponent     actionsBody = null;
        if (showActions) {
            actionsList = actions.isEmpty() ? null : buildActionList(actions, columnId);
            if (actionsList != null) attachMoveMenu(actionsList, columnId, columns);
            actionsBody = actionsList != null ? actionsList : emptyLaneLabel("No actions");
        }

        JComponent projectsBody = null;
        if (showProjects) {
            var subProjects = workspace.getChildren(columnId).stream().filter(NamNode::isProject).toList();
            var projectsList = subProjects.isEmpty() ? null : buildProjectLaneList(subProjects, columnId);
            projectsBody = projectsList != null ? projectsList : emptyLaneLabel("No sub-projects");
        }

        JComponent body;
        if (laneMode == LaneMode.BOTH) {
            var split = new JPanel();
            split.setLayout(new BoxLayout(split, BoxLayout.Y_AXIS));
            actionsBody.setAlignmentX(Component.LEFT_ALIGNMENT);
            projectsBody.setAlignmentX(Component.LEFT_ALIGNMENT);
            clampHeight(actionsBody);
            clampHeight(projectsBody);
            split.add(laneHeader("Actions"));
            split.add(actionsBody);
            split.add(Box.createVerticalStrut(8));
            split.add(laneHeader("Projects"));
            split.add(projectsBody);
            split.add(Box.createVerticalGlue());
            body = split;
        } else {
            body = showActions ? actionsBody : projectsBody;
        }

        // Footer — move button acts on the Actions lane selection (unchanged behavior).
        var movable  = columns.size() > 1;
        var moveBtn  = UiHelper.iconButton("Move action to another column",
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/arrows-exchange.svg")).derive(16, 16));
        moveBtn.setToolTipText("Move selected action to another column");
        final var fList = actionsList;
        moveBtn.setEnabled(movable && fList != null && fList.getSelectedIndex() >= 0);
        if (fList != null) {
            fList.addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                moveBtn.setEnabled(movable && fList.getSelectedIndex() >= 0);
            });
            moveBtn.addActionListener(e -> {
                var action = fList.getSelectedValue();
                if (action == null) return;
                showMoveToColumnMenu(moveBtn, action, columnId, columns, 0, moveBtn.getHeight());
            });
        }
        var footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        footer.add(moveBtn);

        // Drag-and-drop: each lane's list is a drag source + INSERT drop target; the column panel
        // and empty-lane labels also accept drops (append). A drop reparents the node to this column.
        if (movable) {
            column.putClientProperty(COLUMN_ID_KEY, columnId);
            column.setTransferHandler(columnDnD);
            if (actionsBody  != null) wireLaneDnd(actionsBody, columnId);
            if (projectsBody != null) wireLaneDnd(projectsBody, columnId);
        }

        column.add(head,   BorderLayout.NORTH);
        column.add(body,   BorderLayout.CENTER);
        column.add(footer, BorderLayout.SOUTH);
        return column;
    }

    private static final int COLLAPSED_WIDTH = 34;

    /** A collapsed column: a narrow strip with an expand button and the title drawn vertically. */
    private JComponent buildCollapsedColumn(NamNode project, UUID columnId, int actionCount) {
        var strip = new JPanel(new BorderLayout(0, 4)) {
            @Override public Dimension getPreferredSize() { return new Dimension(COLLAPSED_WIDTH, super.getPreferredSize().height); }
            @Override public Dimension getMaximumSize()   { return new Dimension(COLLAPSED_WIDTH, super.getMaximumSize().height); }
            @Override public Dimension getMinimumSize()   { return new Dimension(COLLAPSED_WIDTH, super.getMinimumSize().height); }
        };
        strip.setAlignmentY(Component.TOP_ALIGNMENT);
        strip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(6, 2, 8, 2)));

        var title = project == null ? "Unsorted" : project.getTitle();
        var expandBtn = UiHelper.iconOnlyButton("Expand column: " + title,
                new FlatSVGIcon(ProjectWorkbenchPanel.class.getResource("/icons/chevron-right.svg")).derive(14, 14));
        expandBtn.addActionListener(e -> setColumnCollapsed(columnId, false));

        var vlabel = new VerticalLabel(title + "  ·  " + actionCount);
        vlabel.setToolTipText(title);
        vlabel.setForeground(UIManager.getColor(project == null ? "Label.disabledForeground" : "Label.foreground"));

        strip.add(expandBtn, BorderLayout.NORTH);
        strip.add(vlabel,    BorderLayout.CENTER);
        return strip;
    }

    /** Paints one line of text rotated 90° (reads bottom-to-top), for collapsed column strips. */
    private static final class VerticalLabel extends JComponent {
        private final String text;
        VerticalLabel(String text) {
            this.text = text;
            setFont(UIManager.getFont("Label.font"));
        }
        @Override public Dimension getPreferredSize() {
            var fm = getFontMetrics(getFont());
            return new Dimension(fm.getHeight(), fm.stringWidth(text) + 8);
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont());
            g2.setColor(getForeground());
            var fm = g2.getFontMetrics();
            g2.translate(0, getHeight());
            g2.rotate(-Math.PI / 2);
            int y = (getWidth() + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(text, 4, y);
            g2.dispose();
        }
    }

    private static JLabel emptyLaneLabel(String text) {
        var lbl = new JLabel(text);
        lbl.setForeground(UIManager.getColor("Label.disabledForeground"));
        lbl.setBorder(BorderFactory.createEmptyBorder(12, 4, 12, 4));
        return lbl;
    }

    private static JLabel laneHeader(String text) {
        var l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
        l.setForeground(UIManager.getColor("Label.disabledForeground"));
        l.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static void clampHeight(JComponent c) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
    }

    private void wireLaneDnd(JComponent laneBody, UUID columnId) {
        laneBody.putClientProperty(COLUMN_ID_KEY, columnId);
        laneBody.setTransferHandler(columnDnD);
        if (laneBody instanceof JList<?> jl) {
            jl.setDragEnabled(true);
            jl.setDropMode(DropMode.INSERT);
        }
    }

    /** A column's nested sub-projects: double-click to drill in, drag to reparent to another column. */
    private JList<NamNode> buildProjectLaneList(List<NamNode> projects, UUID columnId) {
        var model = new DefaultListModel<NamNode>();
        for (var p : projects) model.addElement(p);
        var jlist = new JList<>(model);
        jlist.setCellRenderer(new ProjectLaneRenderer());
        jlist.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jlist.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        jlist.setToolTipText("Double-click to open · drag to another column");
        jlist.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                var idx = jlist.locationToIndex(e.getPoint());
                if (idx >= 0) navigateTo(model.getElementAt(idx).getId());
            }
        });
        if (pendingSelection != null) {
            for (int i = 0; i < model.getSize(); i++) {
                if (model.getElementAt(i).getId().equals(pendingSelection)) {
                    jlist.setSelectedIndex(i);
                    pendingSelection = null;
                    break;
                }
            }
        }
        return jlist;
    }

    private static final class ProjectLaneRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            var c = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof NamNode n) c.setText(n.getTitle() + "  ›");
            c.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            return c;
        }
    }

    private void attachMoveMenu(JList<NamNode> list, UUID columnId,
                                java.util.LinkedHashMap<UUID, String> columns) {
        list.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { maybePopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybePopup(e); }
            private void maybePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                var idx = list.locationToIndex(e.getPoint());
                if (idx < 0) return;
                list.setSelectedIndex(idx);
                showMoveToColumnMenu(list, list.getModel().getElementAt(idx), columnId, columns, e.getX(), e.getY());
            }
        });
    }

    private void showMoveToColumnMenu(JComponent source, NamNode action, UUID currentParentId,
                                      java.util.LinkedHashMap<UUID, String> columns, int x, int y) {
        var menu  = new JPopupMenu();
        var title = action.getTitle();
        var label = new JMenuItem("Move \"" + (title.length() <= 40 ? title : title.substring(0, 40) + "…") + "\" to:");
        label.setEnabled(false);
        menu.add(label);
        menu.addSeparator();
        var added = false;
        for (var entry : columns.entrySet()) {
            if (entry.getKey().equals(currentParentId)) continue;
            var item = new JMenuItem(entry.getValue());
            final var targetId = entry.getKey();
            item.addActionListener(e -> moveActionToColumn(action.getId(), targetId));
            menu.add(item);
            added = true;
        }
        if (!added) {
            var none = new JMenuItem("No other column");
            none.setEnabled(false);
            menu.add(none);
        }
        menu.show(source, x, y);
    }

    private void moveActionToColumn(UUID actionId, UUID targetProjectId) {
        try {
            pendingSelection = actionId;
            service.moveNode(actionId, targetProjectId);
            rebuild();
        } catch (IOException ex) { showError(ex.getMessage()); }
    }

    // --- Column-view drag and drop ---

    private static final String COLUMN_ID_KEY = "namdesktop.columnId";

    private static final DataFlavor CARD_FLAVOR = cardFlavor();

    private static DataFlavor cardFlavor() {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + ColumnDragData.class.getName());
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** What a dragged card carries: the action id and the column it came from. */
    private record ColumnDragData(UUID actionId, UUID sourceColumnId) {}

    private static final class CardTransferable implements Transferable {
        private final ColumnDragData data;
        CardTransferable(ColumnDragData data) { this.data = data; }
        @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{ CARD_FLAVOR }; }
        @Override public boolean isDataFlavorSupported(DataFlavor f) { return CARD_FLAVOR.equals(f); }
        @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
            if (!CARD_FLAVOR.equals(f)) throw new UnsupportedFlavorException(f);
            return data;
        }
    }

    /**
     * Shared across all columns: the source/target column id is read from the component's
     * {@link #COLUMN_ID_KEY} client property, and the drop position from the JList drop location.
     * A drop reparents (cross-column) or reorders (same column) via {@code moveNodeBefore}.
     */
    private final class ColumnTransferHandler extends TransferHandler {
        @Override public int getSourceActions(JComponent c) { return MOVE; }

        @Override protected Transferable createTransferable(JComponent c) {
            if (c instanceof JList<?> list && list.getSelectedValue() instanceof NamNode node) {
                return new CardTransferable(new ColumnDragData(node.getId(),
                        (UUID) list.getClientProperty(COLUMN_ID_KEY)));
            }
            return null;
        }

        @Override public boolean canImport(TransferSupport support) {
            return support.isDrop() && support.isDataFlavorSupported(CARD_FLAVOR);
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            final UUID targetCol;
            final ColumnDragData data;
            try {
                data      = (ColumnDragData) support.getTransferable().getTransferData(CARD_FLAVOR);
                targetCol = (UUID) ((JComponent) support.getComponent()).getClientProperty(COLUMN_ID_KEY);
            } catch (UnsupportedFlavorException | java.io.IOException ex) {
                return false;
            }
            if (targetCol == null) return false;

            UUID anchorId = null;
            if (support.getComponent() instanceof JList<?> targetList
                    && support.getDropLocation() instanceof JList.DropLocation dl) {
                var model = targetList.getModel();
                var idx   = dl.getIndex();
                if (idx >= 0 && idx < model.getSize() && model.getElementAt(idx) instanceof NamNode anchor)
                    anchorId = anchor.getId();
            }
            // Dropping a card onto its own slot is a no-op.
            if (data.actionId().equals(anchorId)) return false;

            final UUID anchor = anchorId;
            SwingUtilities.invokeLater(() -> {
                try {
                    pendingSelection = data.actionId();
                    service.moveNodeBefore(data.actionId(), targetCol, anchor);
                    rebuild();
                } catch (IOException ex) { showError(ex.getMessage()); }
            });
            return true;
        }
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

    private static final Icon WB_PENCIL_ICON = new FlatSVGIcon(
            ProjectWorkbenchPanel.class.getResource("/icons/pencil.svg")).derive(12, 12);
    private static final Icon WB_CLIP_ICON = new FlatSVGIcon(
            ProjectWorkbenchPanel.class.getResource("/icons/paperclip.svg")).derive(12, 12);
    private static final int PENCIL_WIDTH = UiHelper.ACTION_PENCIL_W;
    private static final int CLIP_WIDTH   = 16;

    private static final class ActionCellRenderer implements ListCellRenderer<NamNode> {
        private final JPanel panel     = new JPanel(new BorderLayout(6, 0));
        private final JLabel badge     = new JLabel("", SwingConstants.CENTER);
        private final JLabel label     = new JLabel();
        private final JLabel clip      = new JLabel();
        private final JLabel pencil    = new JLabel(WB_PENCIL_ICON);
        private final JPanel east      = new JPanel(new BorderLayout());

        ActionCellRenderer() {
            badge.setOpaque(true);
            badge.setForeground(Color.WHITE);
            badge.setFont(badge.getFont().deriveFont(Font.BOLD, 10f));
            badge.setPreferredSize(new Dimension(BADGE_WIDTH, 0));
            badge.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
            clip.setOpaque(false);
            clip.setPreferredSize(new Dimension(CLIP_WIDTH, 0));
            clip.setHorizontalAlignment(SwingConstants.CENTER);
            pencil.setOpaque(false);
            pencil.setPreferredSize(new Dimension(PENCIL_WIDTH, 0));
            pencil.setHorizontalAlignment(SwingConstants.CENTER);
            east.setOpaque(false);
            east.add(clip,   BorderLayout.CENTER);
            east.add(pencil, BorderLayout.EAST);
            panel.add(badge,  BorderLayout.WEST);
            panel.add(label,  BorderLayout.CENTER);
            panel.add(east,   BorderLayout.EAST);
            panel.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends NamNode> list, NamNode node, int index, boolean isSelected, boolean cellHasFocus) {
            var status = node.getStatus();
            badge.setText(switch (status) {
                case NEXT    -> "Next";
                case BACKLOG -> "Back";
                case DONE    -> "Done";
                default      -> "?";
            });
            var bg = isSelected ? list.getSelectionBackground() : list.getBackground();
            panel.setBackground(bg);
            east.setBackground(bg);
            badge.setBackground(bg);
            badge.setForeground(isSelected ? list.getSelectionForeground() : switch (status) {
                case NEXT    -> BADGE_NEXT;
                case BACKLOG -> BADGE_BACKLOG;
                default      -> BADGE_DONE;
            });
            label.setText(node.getTitle());
            label.setForeground(isSelected ? list.getSelectionForeground()
                                           : status == NodeStatus.DONE ? Color.GRAY : list.getForeground());
            var hasRes = !node.getResources().isEmpty();
            clip.setIcon(hasRes ? WB_CLIP_ICON : null);
            clip.setToolTipText(hasRes ? "Has resources" : null);
            return panel;
        }
    }
}
