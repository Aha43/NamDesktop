package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;
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

        if (parentNode != null || !inherited.isEmpty()) {
            var southPanel = new JPanel();
            southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
            if (parentNode != null)
                southPanel.add(buildProjectRow(parent, parentNode.getId(), parentNode.getTitle(), workspace, service, onChanged));
            if (!inherited.isEmpty())
                southPanel.add(buildInheritedTagsRow(inherited));
            addBelowDescription(southPanel);
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


}
