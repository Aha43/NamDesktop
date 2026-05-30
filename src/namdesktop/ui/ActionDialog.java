package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.Resource;
import namdesktop.model.ResourceType;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ActionDialog extends NodeDialog {

    private final Runnable changeCallback;
    private final NamWorkspaceService actionService;

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
        setTitle("Action: " + workspace.getNode(nodeId).map(n -> n.getTitle()).orElse(""));

        if (showMakeProject) {
            var makeProjectButton = UiHelper.iconButton("Make project",
                    new FlatSVGIcon(ActionDialog.class.getResource("/icons/folder-plus.svg")).derive(16, 16));
            makeProjectButton.setToolTipText("Convert this action into a project");
            makeProjectButton.addActionListener(e -> makeProject(nodeId, service));
            addToolbarButton(makeProjectButton);
        }

        var structural   = structuralIds(workspace);
        var parentNode   = workspace.getParent(nodeId)
                .filter(p -> !structural.contains(p.getId()))
                .orElse(null);
        var ownTags      = workspace.getNode(nodeId).map(n -> n.getTags()).orElse(List.of());
        var inherited    = workspace.effectiveTags(nodeId).stream()
                .filter(t -> !ownTags.contains(t))
                .sorted()
                .toList();

        var southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
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
        setSize(500, 560);
        setLocationRelativeTo(parent);
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

    private JComponent buildResourcesSection(UUID nodeId, NamWorkspace workspace, NamWorkspaceService service) {
        var outer = new JPanel(new BorderLayout());
        outer.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));

        var listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        var typeCombo  = new JComboBox<>(ResourceType.values());
        typeCombo.setSelectedItem(ResourceType.URI);
        var valueField = new JTextField(18);
        var descField  = new JTextField(10);
        descField.setToolTipText("Optional note (shown as tooltip)");

        var content = new JPanel(new BorderLayout(0, 2));
        content.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 4));

        final String[] headerPrefix = {null};
        Runnable[] rebuildList = {null};

        rebuildList[0] = () -> {
            listPanel.removeAll();
            var resources = workspace.getNode(nodeId).map(NamNode::getResources).orElse(List.of());
            for (int i = 0; i < resources.size(); i++) {
                final int idx = i;
                var res = resources.get(i);
                var typeLabel = new JLabel(res.getType().name());
                typeLabel.setFont(typeLabel.getFont().deriveFont(Font.PLAIN, 10f));
                typeLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
                typeLabel.setPreferredSize(new Dimension(40, 0));

                var valueBtn = new JButton("<html><u>" + escapeHtml(res.getValue()) + "</u></html>");
                valueBtn.setBorderPainted(false);
                valueBtn.setContentAreaFilled(false);
                valueBtn.setFocusPainted(false);
                valueBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                if (res.getDescription() != null && !res.getDescription().isBlank())
                    valueBtn.setToolTipText(res.getDescription());
                valueBtn.addActionListener(e -> openResource(res));

                var removeBtn = new JButton("×");
                removeBtn.setFont(removeBtn.getFont().deriveFont(Font.BOLD));
                removeBtn.setMargin(new Insets(0, 4, 0, 4));
                removeBtn.setToolTipText("Remove resource");
                removeBtn.addActionListener(e -> {
                    try { service.removeResource(nodeId, idx); rebuildList[0].run(); }
                    catch (IOException ex) { JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
                });

                var row = new JPanel(new BorderLayout(4, 0));
                row.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
                row.add(typeLabel,  BorderLayout.WEST);
                row.add(valueBtn,   BorderLayout.CENTER);
                row.add(removeBtn,  BorderLayout.EAST);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
                listPanel.add(row);
            }
            listPanel.revalidate();
            listPanel.repaint();
        };
        rebuildList[0].run();

        var addBtn = new JButton("Add");
        addBtn.addActionListener(e -> {
            var value = valueField.getText().strip();
            if (value.isBlank()) return;
            var desc = descField.getText().strip();
            try {
                service.addResource(nodeId, new Resource(
                        (ResourceType) typeCombo.getSelectedItem(), value,
                        desc.isEmpty() ? null : desc));
                valueField.setText("");
                descField.setText("");
                rebuildList[0].run();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        valueField.addActionListener(e -> addBtn.doClick());

        var addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        addRow.add(typeCombo);
        addRow.add(valueField);
        addRow.add(new JLabel("Note:"));
        addRow.add(descField);
        addRow.add(addBtn);

        var scroll = new JScrollPane(listPanel);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(0, 72));

        content.add(addRow,  BorderLayout.NORTH);
        content.add(scroll,  BorderLayout.CENTER);

        var initialCount = workspace.getNode(nodeId).map(n -> n.getResources().size()).orElse(0);
        content.setVisible(initialCount > 0);

        var headerBtn = new JButton();
        headerBtn.setBorderPainted(false);
        headerBtn.setContentAreaFilled(false);
        headerBtn.setHorizontalAlignment(SwingConstants.LEFT);
        headerBtn.setFont(headerBtn.getFont().deriveFont(Font.BOLD));
        headerBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Runnable updateHeader = () -> {
            var count = workspace.getNode(nodeId).map(n -> n.getResources().size()).orElse(0);
            var arrow = content.isVisible() ? "▼" : "▶";
            headerBtn.setText(arrow + " Resources" + (count > 0 ? " (" + count + ")" : ""));
        };
        updateHeader.run();
        headerBtn.addActionListener(e -> {
            content.setVisible(!content.isVisible());
            updateHeader.run();
        });
        // Keep header count in sync after list rebuilds
        var origRebuild = rebuildList[0];
        rebuildList[0] = () -> { origRebuild.run(); updateHeader.run(); };

        outer.add(headerBtn, BorderLayout.NORTH);
        outer.add(content,   BorderLayout.CENTER);
        return outer;
    }

    private void openResource(Resource res) {
        try {
            var desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
            switch (res.getType()) {
                case URI   -> { if (desktop != null) desktop.browse(new java.net.URI(res.getValue())); }
                case EMAIL -> { if (desktop != null) desktop.mail(new java.net.URI("mailto:" + res.getValue())); }
                case FILE  -> { if (desktop != null) desktop.open(new java.io.File(res.getValue())); }
                case TEXT  -> {
                    var sel = new java.awt.datatransfer.StringSelection(res.getValue());
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
                    MainFrame.showNudge("Copied to clipboard");
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
