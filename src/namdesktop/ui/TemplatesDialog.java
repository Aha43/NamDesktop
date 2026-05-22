package namdesktop.ui;

import namdesktop.model.NamWorkspace;
import namdesktop.model.ProjectTemplate;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public final class TemplatesDialog extends JDialog {

    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final JPanel listPanel;

    public TemplatesDialog(Window parent, NamWorkspace workspace, NamWorkspaceService service) {
        super(parent, "Templates", ModalityType.APPLICATION_MODAL);
        this.workspace = workspace;
        this.service   = service;

        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        var scrollPane = new JScrollPane(listPanel);
        scrollPane.setPreferredSize(new Dimension(360, 260));

        var closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(closeButton);

        var footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(closeButton);

        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(footer,     BorderLayout.SOUTH);
        setSize(400, 340);
        setLocationRelativeTo(parent);

        rebuild();
    }

    private void rebuild() {
        listPanel.removeAll();
        var templates = workspace.getTemplates();
        if (templates.isEmpty()) {
            var hint = new JLabel("No templates saved yet.");
            hint.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            listPanel.add(hint);
        } else {
            for (var t : templates) listPanel.add(templateRow(t));
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel templateRow(ProjectTemplate template) {
        var row = new JPanel(new BorderLayout());
        row.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        var name = new JLabel(template.name());
        var count = template.children().size();
        name.setToolTipText(count + " top-level item" + (count == 1 ? "" : "s"));

        var deleteButton = new JButton("Delete");
        deleteButton.addActionListener(e -> {
            var choice = JOptionPane.showConfirmDialog(this,
                    "Delete template \"" + template.name() + "\"?",
                    "Delete template", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
            try {
                service.deleteTemplate(template.name());
                rebuild();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        row.add(name,         BorderLayout.CENTER);
        row.add(deleteButton, BorderLayout.EAST);
        return row;
    }
}
