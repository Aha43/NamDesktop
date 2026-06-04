package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ActionDialog extends NodeDialog {

    private final Runnable changeCallback;
    private final NamWorkspaceService actionService;
    private JTextField dueDateField;

    public ActionDialog(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service) {
        this(parent, nodeId, workspace, service, true, () -> {});
    }

    public ActionDialog(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service, boolean showMakeProject) {
        this(parent, nodeId, workspace, service, showMakeProject, () -> {});
    }

    public ActionDialog(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service, boolean showMakeProject, Runnable onChanged) {
        super(parent, nodeId, workspace, service, onChanged);
        this.changeCallback  = onChanged;
        this.actionService   = service;
        try { service.touchNode(nodeId); } catch (java.io.IOException ignored) {}
        setTitle("Action: " + workspace.getNode(nodeId).map(n -> n.getTitle()).orElse(""));

        if (showMakeProject) {
            var makeProjectButton = UiHelper.iconButton("Make project",
                    new FlatSVGIcon(ActionDialog.class.getResource("/icons/folder-plus.svg")).derive(16, 16));
            makeProjectButton.setToolTipText("Convert this action into a project");
            makeProjectButton.addActionListener(e -> makeProject(nodeId, service));
            addToolbarButton(makeProjectButton);
        }

        var moveButton = UiHelper.iconButton("Move to…",
                new FlatSVGIcon(ActionDialog.class.getResource("/icons/arrows-transfer-up.svg")).derive(16, 16));
        moveButton.setToolTipText("Move this action to a different project");
        moveButton.addActionListener(e -> moveTo(parent, nodeId, workspace, service));
        addToolbarButton(moveButton);

        var structural   = structuralIds(workspace);
        var parentNode   = workspace.getParent(nodeId)
                .filter(p -> !structural.contains(p.getId()))
                .orElse(null);
        var ownTags      = workspace.getNode(nodeId).map(n -> n.getTags()).orElse(List.of());
        var inherited    = workspace.effectiveTags(nodeId).stream()
                .filter(t -> !ownTags.contains(t))
                .sorted()
                .toList();

        var currentDue = workspace.getNode(nodeId).map(n -> n.getDueAt()).orElse(null);
        dueDateField = new JTextField(currentDue != null ? currentDue.toString() : "");
        dueDateField.putClientProperty("JTextField.placeholderText", "No due date");
        dueDateField.setToolTipText("Due date — YYYY-MM-DD, e.g. " + LocalDate.now().plusWeeks(1));

        var southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        southPanel.add(buildDueDateRow());
        if (parentNode != null)
            southPanel.add(buildProjectRow(parent, parentNode.getId(), parentNode.getTitle(), workspace, service, onChanged));
        if (!inherited.isEmpty())
            southPanel.add(buildInheritedTagsRow(inherited));

        var prereqWrapper = new JPanel(new BorderLayout());
        Runnable[] rebuild = {null};
        rebuild[0] = () -> {
            prereqWrapper.removeAll();
            prereqWrapper.add(buildBlockedBySection(parent, nodeId, workspace, service, rebuild));
            prereqWrapper.revalidate();
            prereqWrapper.repaint();
            setDoneButtonEnabled(!actionService.isBlocked(nodeId));
        };
        rebuild[0].run();
        southPanel.add(prereqWrapper);

        var unblocks = service.unblocks(nodeId);
        if (!unblocks.isEmpty()) {
            southPanel.add(buildWouldUnblockSection(parent, nodeId, unblocks, workspace, service));
        }

        southPanel.add(buildResourcesSection(nodeId, workspace, service));

        addBelowDescription(southPanel);
        setSize(500, 650);
        setLocationRelativeTo(parent);
    }

    private JComponent buildDueDateRow() {
        var clearBtn = new JButton("Clear");
        clearBtn.setToolTipText("Remove due date");
        clearBtn.addActionListener(e -> dueDateField.setText(""));

        var row = new JPanel(new BorderLayout(8, 0));
        row.setBorder(BorderFactory.createEmptyBorder(4, 4, 2, 4));
        row.add(new JLabel("Due:"), BorderLayout.WEST);
        row.add(dueDateField,       BorderLayout.CENTER);
        row.add(clearBtn,           BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    @Override
    protected void onSave() throws IOException {
        var text = dueDateField.getText().strip();
        if (text.isEmpty()) {
            actionService.setDueDate(nodeId, null);
        } else {
            try {
                actionService.setDueDate(nodeId, LocalDate.parse(text));
            } catch (DateTimeParseException e) {
                throw new IOException("Invalid date — use YYYY-MM-DD format (e.g. " + LocalDate.now().plusWeeks(1) + ")");
            }
        }
    }

    private JComponent buildProjectRow(Window parent, UUID projectId, String projectTitle,
                                       NamWorkspace workspace, NamWorkspaceService service, Runnable onChanged) {
        var path = workspace.buildPath(projectId);
        var breadcrumb = path.stream().skip(1).map(n -> n.getTitle()).collect(Collectors.joining(" > "));

        var label = new JLabel("Project: " + projectTitle);
        label.setToolTipText(breadcrumb);

        var openButton = UiHelper.iconButton("Open project",
                new FlatSVGIcon(ActionDialog.class.getResource("/icons/arrow-right.svg")).derive(16, 16));
        openButton.setToolTipText("Open project: " + projectTitle);
        openButton.addActionListener(e -> {
            dispose();
            new ProjectDialog(parent, projectId, workspace, service, onChanged).setVisible(true);
        });

        var row = new JPanel(new BorderLayout(8, 0));
        row.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        row.add(label,      BorderLayout.CENTER);
        row.add(openButton, BorderLayout.EAST);
        return row;
    }

    private static JComponent buildInheritedTagsRow(List<String> inherited) {
        var label = new JLabel("<html><i>Project tags: " + String.join(", ", inherited) + "</i></html>");
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        label.setToolTipText("Tags inherited from ancestor projects — read only");
        var row = new JPanel(new BorderLayout());
        row.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));
        row.add(label, BorderLayout.CENTER);
        return row;
    }

    private static Set<UUID> structuralIds(NamWorkspace workspace) {
        return Set.of(workspace.getRootNodeId(), workspace.getInboxNodeId(),
                workspace.getProjectsNodeId(), workspace.getNextActionsNodeId())
                .stream().filter(id -> id != null).collect(Collectors.toSet());
    }

    @Override
    protected void onMarkedDone(UUID nodeId, NamWorkspaceService service) {
        var unblocked = service.newlyUnblockedNames(nodeId);
        if (!unblocked.isEmpty())
            MainFrame.showNudge("Unblocked: " + String.join(", ", unblocked));
    }

    private JComponent buildBlockedBySection(Window parent, UUID nodeId, NamWorkspace workspace,
                                               NamWorkspaceService service, Runnable[] rebuild) {
        var panel = new JPanel(new BorderLayout(0, 2));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Blocked by"),
                BorderFactory.createEmptyBorder(2, 4, 4, 4)));

        var structural = structuralIds(workspace);
        var searchField = new PrerequisitePickerField(this,
                () -> {
                    var already = workspace.getNode(nodeId).map(n -> n.getBlockedBy()).orElse(List.of());
                    return workspace.getNodes().values().stream()
                            .filter(n -> !n.isProject())
                            .filter(n -> !structural.contains(n.getId()))
                            .filter(n -> !already.contains(n.getId()))
                            .filter(n -> n.getTitle() != null && !n.getTitle().isBlank())
                            .filter(n -> service.canAddPrerequisite(nodeId, n.getId()))
                            .map(n -> new PrerequisitePickerField.Candidate(n.getId(), n.getTitle()))
                            .sorted(java.util.Comparator.comparing(PrerequisitePickerField.Candidate::title))
                            .toList();
                },
                prereqId -> {
                    try {
                        service.addPrerequisite(nodeId, prereqId);
                        notifyChanged();
                        rebuild[0].run();
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this, "Failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                });
        searchField.setToolTipText("Type to search for a prerequisite action");
        panel.add(searchField, BorderLayout.NORTH);

        var prereqIds = workspace.getNode(nodeId).map(n -> new ArrayList<>(n.getBlockedBy())).orElse(new ArrayList<>());
        if (!prereqIds.isEmpty()) {
            var listPanel = new JPanel();
            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
            for (var prereqId : prereqIds) {
                workspace.getNode(prereqId).ifPresent(prereq -> {
                    var row = new JPanel(new BorderLayout(4, 0));
                    row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
                    var link = new JButton("<html><u>" + prereq.getTitle() + "</u></html>");
                    link.setBorderPainted(false);
                    link.setContentAreaFilled(false);
                    link.setFocusPainted(false);
                    link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    link.setToolTipText("Open action: " + prereq.getTitle());
                    link.addActionListener(e -> {
                        dispose();
                        new ActionDialog(parent, prereq.getId(), workspace, service, true, changeCallback).setVisible(true);
                    });
                    row.add(link, BorderLayout.CENTER);
                    var removeBtn = new JButton("×");
                    removeBtn.setFont(removeBtn.getFont().deriveFont(Font.BOLD));
                    removeBtn.setMargin(new Insets(0, 5, 0, 5));
                    removeBtn.setToolTipText("Remove prerequisite");
                    removeBtn.addActionListener(e -> {
                        try {
                            service.removePrerequisite(nodeId, prereq.getId());
                            notifyChanged();
                            rebuild[0].run();
                        } catch (IOException ex) {
                            JOptionPane.showMessageDialog(this, "Failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                    row.add(removeBtn, BorderLayout.EAST);
                    listPanel.add(row);
                });
            }
            var scroll = new JScrollPane(listPanel);
            scroll.setBorder(null);
            scroll.setPreferredSize(new Dimension(0, Math.min(prereqIds.size() * 30 + 4, 94)));
            panel.add(scroll, BorderLayout.CENTER);
        }

        return panel;
    }

    private JComponent buildWouldUnblockSection(Window parent, UUID nodeId, List<UUID> unblockIds,
                                                  NamWorkspace workspace, NamWorkspaceService service) {
        var panel = new JPanel(new BorderLayout(0, 2));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Would unblock"),
                BorderFactory.createEmptyBorder(2, 4, 4, 4)));

        var linksPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (var unblockedId : unblockIds) {
            workspace.getNode(unblockedId).ifPresent(unblocked -> {
                var link = new JButton("<html><u>" + unblocked.getTitle() + "</u></html>");
                link.setBorderPainted(false);
                link.setContentAreaFilled(false);
                link.setFocusPainted(false);
                link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                link.setToolTipText("Open action: " + unblocked.getTitle());
                link.addActionListener(e -> {
                    dispose();
                    new ActionDialog(parent, unblockedId, workspace, service, true, changeCallback).setVisible(true);
                });
                linksPanel.add(link);
            });
        }

        panel.add(linksPanel, BorderLayout.CENTER);
        return panel;
    }

    private void makeProject(UUID nodeId, NamWorkspaceService service) {
        try {
            service.convertNextActionToProject(nodeId);
            notifyChanged();
            dispose();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to convert: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void moveTo(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service) {
        var picked = MoveToDialog.pickProject(parent, nodeId, workspace, "(Free action)", workspace.getNextActionsNodeId());
        if (picked == null) return;
        try {
            service.moveNode(nodeId, picked);
            notifyChanged();
            dispose();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to move: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

}
