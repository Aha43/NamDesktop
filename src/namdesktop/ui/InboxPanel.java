package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import namdesktop.app.AppSettings;
import namdesktop.lens.InboxItemRow;
import namdesktop.lens.InboxLens;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.model.ProjectTemplate;
import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public final class InboxPanel extends JPanel {

    private final NamWorkspace workspace;
    private final NamWorkspaceService service;
    private final InboxTableModel tableModel;
    private JTable table;
    private JButton processButton;
    private JPanel tableCard;
    private String sortOrder;

    public InboxPanel(NamWorkspace workspace, NamWorkspaceService service) {
        super(new BorderLayout());
        this.workspace  = workspace;
        this.service    = service;
        this.tableModel = new InboxTableModel();
        this.sortOrder  = AppSettings.getInstance().getInboxSortOrder();

        table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                var c = super.prepareRenderer(renderer, row, column);
                var status = tableModel.getRow(row).status();
                c.setForeground(status == NodeStatus.DONE ? Color.GRAY : getForeground());
                return c;
            }
        };
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(1).setCellRenderer(UiHelper.ageRenderer(true));
        table.getColumnModel().getColumn(1).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setMaxWidth(50);
        table.getColumnModel().getColumn(2).setCellRenderer(UiHelper.paperclipRenderer());
        table.getColumnModel().getColumn(2).setPreferredWidth(18);
        table.getColumnModel().getColumn(2).setMaxWidth(18);
        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { if (e.isPopupTrigger()) showMenu(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showMenu(e); }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    var row = table.rowAtPoint(e.getPoint());
                    if (row >= 0 && tableModel.getRow(row).status() != NodeStatus.DONE)
                        process(tableModel.getRow(row));
                }
            }
        });

        table.getSelectionModel().addListSelectionListener(ev -> {
            if (ev.getValueIsAdjusting()) return;
            var row = table.getSelectedRow();
            processButton.setEnabled(row >= 0 && tableModel.getRow(row).status() != NodeStatus.DONE);
        });

        var toolbar = new JToolBar();
        toolbar.setFloatable(false);
        processButton = UiHelper.iconButton("Process…",
                new FlatSVGIcon(InboxPanel.class.getResource("/icons/arrow-right.svg")).derive(16, 16));
        processButton.setToolTipText("Decide what this item is and where it belongs");
        processButton.setEnabled(false);
        processButton.addActionListener(e -> {
            var row = table.getSelectedRow();
            if (row >= 0) process(tableModel.getRow(row));
        });
        toolbar.add(processButton);
        toolbar.addSeparator();
        var addButton = UiHelper.iconButton("Add item",
                new FlatSVGIcon(InboxPanel.class.getResource("/icons/plus.svg")).derive(16, 16));
        var modifier = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
                == java.awt.event.InputEvent.META_DOWN_MASK ? "⌘" : "Ctrl";
        addButton.setToolTipText("Add new inbox item (" + modifier + "+I)");
        addButton.addActionListener(e -> addItem());
        toolbar.add(addButton);
        toolbar.add(Box.createHorizontalGlue());
        var clockUpIcon   = new FlatSVGIcon(InboxPanel.class.getResource("/icons/clock-up.svg")).derive(16, 16);
        var clockDownIcon = new FlatSVGIcon(InboxPanel.class.getResource("/icons/clock-down.svg")).derive(16, 16);
        var sortBtn = new JToggleButton(clockUpIcon);
        sortBtn.setSelected(!sortOrder.equals("NONE"));
        sortBtn.setIcon(sortOrder.equals("LIFO") ? clockDownIcon : clockUpIcon);
        sortBtn.setToolTipText(sortTooltip(sortOrder));
        sortBtn.addActionListener(e -> {
            sortOrder = switch (sortOrder) { case "NONE" -> "FIFO"; case "FIFO" -> "LIFO"; default -> "NONE"; };
            sortBtn.setSelected(!sortOrder.equals("NONE"));
            sortBtn.setIcon(sortOrder.equals("LIFO") ? clockDownIcon : clockUpIcon);
            sortBtn.setToolTipText(sortTooltip(sortOrder));
            AppSettings.getInstance().setInboxSortOrder(sortOrder);
            try { AppSettings.getInstance().save(); } catch (IOException ignored) {}
            refresh();
        });
        toolbar.add(sortBtn);

        tableCard = UiHelper.tableCard(new JScrollPane(table), "Nothing captured yet. Use + to add something.");
        add(toolbar,    BorderLayout.NORTH);
        add(tableCard,  BorderLayout.CENTER);
    }

    public void triggerAdd() { addItem(); }

    public void refresh() {
        var rows = new InboxLens().items(workspace);
        if (!sortOrder.equals("NONE")) {
            var cmp = Comparator.comparing(
                    (InboxItemRow r) -> r.updatedAt() != null ? r.updatedAt() : r.createdAt(),
                    Comparator.nullsLast(sortOrder.equals("FIFO")
                            ? Comparator.<LocalDateTime>naturalOrder()
                            : Comparator.<LocalDateTime>reverseOrder()));
            rows = rows.stream().sorted(cmp).toList();
        }
        tableModel.setRows(rows);
        UiHelper.setTableEmpty(tableCard, tableModel.getRowCount() == 0);
    }

    private static String sortTooltip(String order) {
        return switch (order) { case "FIFO" -> "Newest first"; case "LIFO" -> "Remove sort"; default -> "Oldest first"; };
    }

    private void showMenu(MouseEvent e) {
        var row = table.rowAtPoint(e.getPoint());
        if (row < 0) return;
        table.setRowSelectionInterval(row, row);

        var selected = tableModel.getRow(row);
        var processItem  = new JMenuItem("Process…");
        var renameItem   = new JMenuItem("Rename");
        var markDoneItem = new JMenuItem("Mark done");
        var deleteItem   = new JMenuItem("Delete");

        var isDone = selected.status() == NodeStatus.DONE;
        processItem.setEnabled(!isDone);
        markDoneItem.setEnabled(!isDone);

        processItem.addActionListener(ev  -> process(selected));
        renameItem.addActionListener(ev   -> rename(selected));
        markDoneItem.addActionListener(ev -> markDone(selected));
        deleteItem.addActionListener(ev   -> delete(selected));

        var menu = new JPopupMenu();
        menu.add(processItem);
        menu.add(renameItem);
        menu.add(markDoneItem);
        menu.addSeparator();
        menu.add(deleteItem);
        menu.show(table, e.getX(), e.getY());
    }

    private void process(InboxItemRow row) {
        var result = ProcessInboxDialog.show(parent(), row.title());
        try {
            switch (result) {
                case NEXT_ACTION -> {
                    service.convertInboxItemToNextAction(row.id());
                    refresh();
                    MainFrame.showNudge("Added to Next Actions");
                }
                case PARK_FOR_LATER -> {
                    service.convertInboxItemToNextAction(row.id());
                    service.markBacklog(row.id());
                    refresh();
                    MainFrame.showNudge("Parked for later");
                }
                case PROJECT -> {
                    service.convertInboxItemToProject(row.id());
                    var template = pickTemplate();
                    if (template != null) service.applyTemplate(row.id(), template);
                    refresh();
                    MainFrame.showNudge("Created project “" + row.title() + "”");
                    new ProjectDialog(parent(), row.id(), workspace, service, this::refresh).setVisible(true);
                }
                case CANCELLED -> {}
            }
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private ProjectTemplate pickTemplate() {
        var templates = workspace.getTemplates();
        if (templates.isEmpty()) return null;
        var names = new String[templates.size() + 1];
        names[0] = "No template";
        for (int i = 0; i < templates.size(); i++) names[i + 1] = templates.get(i).name();
        var choice = JOptionPane.showOptionDialog(parent(),
                "Apply a project template?", "Choose template",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, names, names[0]);
        if (choice <= 0) return null;
        return templates.get(choice - 1);
    }

    private void addItem() {
        var area = new JTextArea(4, 32);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setToolTipText("One item per line · Ctrl+Enter to confirm");

        var confirmBtn = new JButton("Add");
        var cancelBtn  = new JButton("Cancel");

        area.getInputMap().put(
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER,
                        java.awt.event.InputEvent.CTRL_DOWN_MASK), "confirm");
        area.getActionMap().put("confirm", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { confirmBtn.doClick(); }
        });

        var panel = new JPanel(new BorderLayout(0, 4));
        panel.add(new JLabel("One item per line:"), BorderLayout.NORTH);
        panel.add(new JScrollPane(area),            BorderLayout.CENTER);

        var dialog = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION, null,
                new Object[]{confirmBtn, cancelBtn});
        var window = dialog.createDialog(parent(), "Add to Inbox");

        boolean[] confirmed = {false};
        confirmBtn.addActionListener(e -> { confirmed[0] = true; window.dispose(); });
        cancelBtn.addActionListener(e  -> window.dispose());

        SwingUtilities.invokeLater(area::requestFocusInWindow);
        window.setVisible(true);

        if (!confirmed[0]) return;
        var lines = area.getText().lines()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
        if (lines.isEmpty()) return;
        try {
            for (var line : lines) service.addInboxItem(line);
            refresh();
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private void rename(InboxItemRow row) {
        var title = (String) JOptionPane.showInputDialog(
                parent(), "Enter new title:", "Rename",
                JOptionPane.PLAIN_MESSAGE, null, null, row.title());
        if (title == null || title.isBlank()) return;
        try {
            service.renameNode(row.id(), title.strip());
            refresh();
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private void markDone(InboxItemRow row) {
        try {
            service.markDone(row.id());
            refresh();
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private void delete(InboxItemRow row) {
        try {
            service.deleteLeaf(row.id());
            refresh();
        } catch (IllegalStateException e) {
            showError("This item has sub-items and cannot be deleted directly.");
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private Window parent() {
        return SwingUtilities.getWindowAncestor(this);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(parent(), message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static final class InboxTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Title", "Age", ""};
        private List<InboxItemRow> rows = List.of();

        void setRows(List<InboxItemRow> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        InboxItemRow getRow(int index) { return rows.get(index); }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Class<?> getColumnClass(int col) {
            if (col == 1) return Long.class;
            if (col == 2) return Boolean.class;
            return String.class;
        }

        @Override
        public Object getValueAt(int row, int col) {
            var r = rows.get(row);
            return switch (col) {
                case 0 -> r.title();
                case 1 -> UiHelper.ageDays(r.updatedAt(), r.createdAt());
                case 2 -> r.hasResources();
                default -> null;
            };
        }
    }
}
