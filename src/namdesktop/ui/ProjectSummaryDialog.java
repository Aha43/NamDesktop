package namdesktop.ui;

import namdesktop.lens.ProjectSummary;
import namdesktop.model.NamWorkspace;

import javax.swing.*;
import java.awt.*;
import java.util.UUID;

/**
 * Shows a project's Markdown summary (#404) with a live preview, an "include sub-projects" toggle,
 * and a copy-to-clipboard button — so you can see exactly what you're copying before you do.
 */
final class ProjectSummaryDialog extends JDialog {

    private ProjectSummaryDialog(Window parent, NamWorkspace workspace, UUID projectId, String projectName) {
        super(parent, "Project summary — " + projectName, ModalityType.APPLICATION_MODAL);

        var area = new JTextArea(22, 64);
        area.setEditable(false);
        area.setLineWrap(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        var includeSubs = new JCheckBox("Include sub-projects", true);
        Runnable refresh = () -> {
            area.setText(ProjectSummary.markdown(workspace, projectId, includeSubs.isSelected()));
            area.setCaretPosition(0);
        };
        includeSubs.addActionListener(e -> refresh.run());
        refresh.run();

        var copyButton = new JButton("Copy to clipboard");
        copyButton.addActionListener(e -> {
            var sel = new java.awt.datatransfer.StringSelection(area.getText());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            MainFrame.showNudge("Summary copied to clipboard");
        });
        var closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());

        var top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        top.add(includeSubs);

        var footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.add(copyButton);
        footer.add(closeButton);

        var content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(top,                  BorderLayout.NORTH);
        content.add(new JScrollPane(area), BorderLayout.CENTER);
        content.add(footer,               BorderLayout.SOUTH);
        setContentPane(content);

        getRootPane().setDefaultButton(copyButton);
        pack();
        setLocationRelativeTo(parent);
    }

    static void show(Window parent, NamWorkspace workspace, UUID projectId, String projectName) {
        new ProjectSummaryDialog(parent, workspace, projectId, projectName).setVisible(true);
    }
}
