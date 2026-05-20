package namdesktop.ui;

import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ActionDialog extends NodeDialog {

    public ActionDialog(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service) {
        this(parent, nodeId, workspace, service, true, () -> {});
    }

    public ActionDialog(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service, boolean showMakeProject) {
        this(parent, nodeId, workspace, service, showMakeProject, () -> {});
    }

    public ActionDialog(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service, boolean showMakeProject, Runnable onChanged) {
        super(parent, nodeId, workspace, service, onChanged);

        if (showMakeProject) {
            var makeProjectButton = new JButton("Make project");
            makeProjectButton.addActionListener(e -> makeProject(nodeId, service));
            addToolbarButton(makeProjectButton);

            var backlogButton = new JButton("Move to backlog");
            backlogButton.addActionListener(e -> moveToBacklog(nodeId, service));
            addToolbarButton(backlogButton);
        }

        var structural = structuralIds(workspace);
        workspace.getParent(nodeId)
                .filter(p -> !structural.contains(p.getId()))
                .ifPresent(projectNode -> addContextRow(parent, nodeId, projectNode.getId(), projectNode.getTitle(), workspace, service, onChanged));
    }

    private void addContextRow(Window parent, UUID actionId, UUID projectId, String projectTitle,
                               NamWorkspace workspace, NamWorkspaceService service, Runnable onChanged) {
        var path = workspace.buildPath(projectId);
        var breadcrumb = path.stream().skip(1).map(n -> n.getTitle()).collect(Collectors.joining(" > "));

        var label = new JLabel("Project: " + projectTitle);
        label.setToolTipText(breadcrumb);

        var openButton = new JButton("Open project");
        openButton.addActionListener(e -> {
            dispose();
            new ProjectDialog(parent, projectId, workspace, service, onChanged, actionId).setVisible(true);
        });

        var row = new JPanel(new BorderLayout(8, 0));
        row.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        row.add(label,      BorderLayout.CENTER);
        row.add(openButton, BorderLayout.EAST);

        addBelowDescription(row);
    }

    private static Set<UUID> structuralIds(NamWorkspace workspace) {
        return Set.of(workspace.getRootNodeId(), workspace.getInboxNodeId(),
                workspace.getProjectsNodeId(), workspace.getNextActionsNodeId())
                .stream().filter(id -> id != null).collect(Collectors.toSet());
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

    private void moveToBacklog(UUID nodeId, NamWorkspaceService service) {
        try {
            service.markBacklog(nodeId);
            notifyChanged();
            dispose();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
