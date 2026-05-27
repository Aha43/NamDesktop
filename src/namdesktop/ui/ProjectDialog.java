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

    private final UUID nodeId;
    private final NamWorkspaceService service;

    public ProjectDialog(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service) {
        this(parent, nodeId, workspace, service, () -> {});
    }

    public ProjectDialog(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service, Runnable onChanged) {
        super(parent, nodeId, workspace, service, onChanged);
        this.nodeId  = nodeId;
        this.service = service;
        setTitle("Project: " + workspace.getNode(nodeId).map(n -> n.getTitle()).orElse(""));
        hideStatusButton();

        var convertButton = UiHelper.iconButton("Convert to action",
                new FlatSVGIcon(ProjectDialog.class.getResource("/icons/arrow-right.svg")).derive(16, 16));
        convertButton.setToolTipText("Convert this project to an action");
        convertButton.addActionListener(e -> convertToAction());
        addToolbarButton(convertButton);

        var saveTemplateButton = UiHelper.iconButton("Save as Template…",
                new FlatSVGIcon(ProjectDialog.class.getResource("/icons/copy.svg")).derive(16, 16));
        saveTemplateButton.setToolTipText("Save this project's structure as a reusable template");
        saveTemplateButton.addActionListener(e -> saveAsTemplate());
        addToolbarButton(saveTemplateButton);

        setSize(500, 350);
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
}
