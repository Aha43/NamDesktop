package namdesktop.ui;

import namdesktop.model.NamWorkspace;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.UUID;

public final class ProjectDialog extends NodeDialog {

    private final UUID nodeId;
    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final DefaultListModel<String> listModel;
    private final JList<String> childList;
    private java.util.List<UUID> childIds;

    public ProjectDialog(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service) {
        this(parent, nodeId, workspace, service, () -> {});
    }

    public ProjectDialog(Window parent, UUID nodeId, NamWorkspace workspace, NamWorkspaceService service, Runnable onChanged) {
        super(parent, nodeId, workspace, service, onChanged);
        this.nodeId    = nodeId;
        this.workspace = workspace;
        this.service   = service;

        listModel = new DefaultListModel<>();
        childList = new JList<>(listModel);
        childList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        childList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                var index = childList.locationToIndex(e.getPoint());
                if (index < 0) return;
                var childId = childIds.get(index);
                new ActionDialog(ProjectDialog.this, childId, workspace, service, false).setVisible(true);
                refreshChildList();
            }
        });

        var addActionButton = new JButton("Add action");
        addActionButton.addActionListener(e -> addAction());
        var actionsToolbar = new JToolBar();
        actionsToolbar.setFloatable(false);
        actionsToolbar.add(addActionButton);

        var actionsPanel = new JPanel(new BorderLayout());
        actionsPanel.setBorder(BorderFactory.createTitledBorder("Actions"));
        actionsPanel.setPreferredSize(new Dimension(0, 180));
        actionsPanel.add(actionsToolbar,             BorderLayout.NORTH);
        actionsPanel.add(new JScrollPane(childList), BorderLayout.CENTER);

        addBelowDescription(actionsPanel);
        setSize(500, 550);

        refreshChildList();
    }

    private void addAction() {
        var title = JOptionPane.showInputDialog(this, "Action title:", "Add action", JOptionPane.PLAIN_MESSAGE);
        if (title == null || title.isBlank()) return;
        try {
            service.addChild(nodeId, title.strip());
            refreshChildList();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshChildList() {
        childIds = workspace.getChildren(nodeId).stream()
                .map(n -> n.getId())
                .collect(java.util.stream.Collectors.toList());
        listModel.clear();
        workspace.getChildren(nodeId).forEach(n -> listModel.addElement(n.getTitle()));
    }
}
