package namdesktop.ui;

import namdesktop.model.NamWorkspace;

import javax.swing.*;
import java.awt.*;
import java.util.List;

final class MissionControlCreateDialog extends JDialog {

    private final JTextField nameField;
    private final TagsField  tagsField;
    private boolean confirmed;

    MissionControlCreateDialog(Window owner, NamWorkspace workspace) {
        super(owner, "New Mission Control", ModalityType.APPLICATION_MODAL);
        nameField = new JTextField(24);
        tagsField = new TagsField(owner, workspace::allTags);

        var form = new JPanel(new GridBagLayout());
        var gbc  = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        form.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        form.add(new JLabel("Tags (OR):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        form.add(tagsField, gbc);

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
        pack();
        setMinimumSize(new Dimension(360, getHeight()));
        setLocationRelativeTo(owner);
    }

    boolean isConfirmed() { return confirmed; }
    String getMcName()    { return nameField.getText().strip(); }
    List<String> getMcTags() { return tagsField.getTags(); }
}
