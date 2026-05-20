package namdesktop.ui;

import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.UUID;

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
