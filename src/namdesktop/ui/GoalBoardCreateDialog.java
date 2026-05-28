package namdesktop.ui;

import namdesktop.model.NamWorkspace;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.List;
import java.util.Set;

final class GoalBoardCreateDialog extends JDialog {

    private final JTextField nameField;
    private final TagsField  tagsField;
    private final DefaultListModel<String> previewModel = new DefaultListModel<>();
    private final JLabel previewHint;
    private final NamWorkspace workspace;
    private boolean confirmed;

    GoalBoardCreateDialog(Window owner, NamWorkspace workspace) {
        super(owner, "New Goal Board", ModalityType.APPLICATION_MODAL);
        this.workspace = workspace;
        nameField  = new JTextField(24);
        tagsField  = new TagsField(owner, workspace::allTags);
        previewHint = new JLabel("Add tags above to preview matching projects.");
        previewHint.setFont(previewHint.getFont().deriveFont(Font.ITALIC, 11f));
        previewHint.setForeground(UIManager.getColor("Label.disabledForeground"));

        var previewList   = new JList<>(previewModel);
        previewList.setVisibleRowCount(4);
        previewList.setEnabled(false);
        var previewScroll = new JScrollPane(previewList);

        var form = new JPanel(new GridBagLayout());
        var gbc  = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;

        gbc.insets = new Insets(6, 8, 4, 8);
        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        form.add(new JLabel("Tags (OR):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        form.add(tagsField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 2, 8);
        form.add(previewHint, gbc);

        gbc.gridy = 2; gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 1;
        gbc.insets = new Insets(0, 8, 6, 8);
        form.add(previewScroll, gbc);

        gbc.gridy = 3; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0; gbc.weighty = 0;
        gbc.gridx = 0; gbc.insets = new Insets(4, 8, 8, 8);
        form.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        form.add(nameField, gbc);

        var okButton     = new JButton("Create");
        var cancelButton = new JButton("Cancel");
        okButton.addActionListener(e -> { confirmed = true; dispose(); });
        cancelButton.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(okButton);

        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancelButton);
        buttons.add(okButton);

        add(form,    BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        tagsField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { SwingUtilities.invokeLater(GoalBoardCreateDialog.this::updatePreview); }
            @Override public void removeUpdate(DocumentEvent e)  { SwingUtilities.invokeLater(GoalBoardCreateDialog.this::updatePreview); }
            @Override public void changedUpdate(DocumentEvent e) {}
        });

        pack();
        setMinimumSize(new Dimension(380, getHeight()));
        setLocationRelativeTo(owner);
    }

    private void updatePreview() {
        var tags = tagsField.getTags();
        previewModel.clear();
        if (tags.isEmpty()) {
            previewHint.setText("Add tags above to preview matching projects.");
            return;
        }
        var tagSet  = Set.copyOf(tags);
        var matches = workspace.getNodes().values().stream()
                .filter(n -> n.isProject() && n.getTags().stream().anyMatch(tagSet::contains))
                .map(n -> n.getTitle())
                .sorted()
                .toList();
        if (matches.isEmpty()) {
            previewHint.setText("No projects with these tags yet.");
        } else {
            previewHint.setText(matches.size() + " project" + (matches.size() != 1 ? "s" : "") + " will appear:");
            matches.forEach(previewModel::addElement);
        }
    }

    boolean isConfirmed()        { return confirmed; }
    String getGoalBoardName()    { return nameField.getText().strip(); }
    List<String> getGoalBoardTags() { return tagsField.getTags(); }
}
