package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.UUID;

import static javax.swing.JOptionPane.*;

public final class ProjectDialog extends NodeDialog {

    private final NamWorkspace workspace;

    public ProjectDialog(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service) {
        this(parent, nodeId, workspace, service, () -> {});
    }

    public ProjectDialog(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service, Runnable onChanged) {
        super(parent, nodeId, workspace, service, onChanged);
        this.workspace = workspace;
        try { service.touchNode(nodeId); } catch (java.io.IOException ignored) {}
        setTitle("Project: " + workspace.getNode(nodeId).map(n -> n.getTitle()).orElse(""));
        hideStatusButton();

        var convertButton = UiHelper.iconButton("Convert to action",
                new FlatSVGIcon(ProjectDialog.class.getResource("/icons/arrow-right.svg")).derive(16, 16));
        convertButton.setToolTipText("Convert this project to an action");
        convertButton.addActionListener(e -> convertToAction());
        addToolbarButton(convertButton);

        var moveButton = UiHelper.iconButton("Move to…",
                new FlatSVGIcon(ProjectDialog.class.getResource("/icons/arrows-transfer-up.svg")).derive(16, 16));
        moveButton.setToolTipText("Move this project under a different parent");
        moveButton.addActionListener(e -> moveTo(parent, nodeId));
        addToolbarButton(moveButton);

        var saveTemplateButton = UiHelper.iconButton("Save as Template…",
                new FlatSVGIcon(ProjectDialog.class.getResource("/icons/copy.svg")).derive(16, 16));
        saveTemplateButton.setToolTipText("Save this project's structure as a reusable template");
        saveTemplateButton.addActionListener(e -> saveAsTemplate());
        addToolbarButton(saveTemplateButton);

        var southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        southPanel.add(buildResourcesSection(nodeId, workspace, service));
        addBelowDescription(southPanel);
        setSize(500, 460);
    }

    private void saveAsTemplate() {
        var name = JOptionPane.showInputDialog(this, "Template name:", "Save as Template", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) return;
        try {
            service.saveAsTemplate(name.strip(), nodeId);
            JOptionPane.showMessageDialog(this, "Template \"" + name.strip() + "\" saved.",
                    "Template saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    protected String deleteConfirmMessage(String title) {
        var subtree  = workspace.collectSubtree(nodeId);
        var projects = (int) subtree.stream().skip(1)
                .map(workspace::getNode).flatMap(java.util.Optional::stream)
                .filter(n -> n.isProject()).count();
        var actions  = (int) subtree.stream().skip(1)
                .map(workspace::getNode).flatMap(java.util.Optional::stream)
                .filter(n -> !n.isProject()).count();
        if (projects == 0 && actions == 0)
            return "Delete \"" + title + "\"? This cannot be undone.";
        var parts = new java.util.ArrayList<String>();
        if (projects > 0) parts.add(projects + " sub-project" + (projects > 1 ? "s" : ""));
        if (actions  > 0) parts.add(actions  + " action"      + (actions  > 1 ? "s" : ""));
        return "Delete \"" + title + "\"? This will also permanently remove "
                + String.join(" and ", parts) + ".";
    }

    @Override
    protected void doDelete() {
        try {
            service.deleteRecursive(nodeId);
            notifyChanged();
            dispose();
        } catch (IOException e) {
            showMessageDialog(this, "Failed to delete: " + e.getMessage(), "Error", ERROR_MESSAGE);
        }
    }

    private void convertToAction() {
        try {
            service.convertProjectToAction(nodeId);
            notifyChanged();
            dispose();
        } catch (IllegalStateException e) {
            showMessageDialog(this, "This project still has actions. Remove them before converting.",
                    "Cannot convert", ERROR_MESSAGE);
        } catch (IOException e) {
            showMessageDialog(this, "Failed to save: " + e.getMessage(), "Error", ERROR_MESSAGE);
        }
    }

    private void moveTo(Window parent, UUID nodeId) {
        var picked = MoveToDialog.pickProject(parent, nodeId, workspace, true);
        if (picked == null) return;
        try {
            service.moveNode(nodeId, picked);
            notifyChanged();
            dispose();
        } catch (IOException e) {
            showMessageDialog(this, "Failed to move: " + e.getMessage(), "Error", ERROR_MESSAGE);
        }
    }
}
