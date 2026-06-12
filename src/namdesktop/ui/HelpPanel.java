package namdesktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.io.IOException;
import java.util.List;

public final class HelpPanel extends JPanel {

    private sealed interface Item permits Entry, SectionHeader {}
    private record Entry(String title, String slug) implements Item {}
    private record SectionHeader(String label)      implements Item {}

    private static final List<Item> ITEMS = List.of(
            new SectionHeader("Tutorial"),
            new Entry("Getting started",    "tutorials/getting-started"),

            new SectionHeader("Daily workflow"),
            new Entry("Inbox",              "inbox"),
            new Entry("Next Actions",       "next-actions"),
            new Entry("Backlog",            "backlog"),
            new Entry("Due Actions",        "due"),
            new Entry("Done",               "done"),
            new Entry("Focus mode",         "focus-mode"),

            new SectionHeader("Projects"),
            new Entry("Projects",           "projects"),
            new Entry("Project Workbench",  "workbench"),
            new Entry("Column view",        "column-view"),
            new Entry("Templates",          "templates"),
            new Entry("Blocked actions",    "blocked"),
            new Entry("Resources",          "resources"),
            new Entry("Goal Board",         "mission-control"),

            new SectionHeader("Finding work"),
            new Entry("Tag filter",         "contexts"),
            new Entry("Saved Views",        "saved-views"),
            new Entry("Search",             "search"),
            new Entry("Goal Board",         "mission-control"),
            new Entry("Due Actions",        "due"),
            new Entry("Blocked actions",    "blocked"),

            new SectionHeader("App"),
            new Entry("Focus mode",         "focus-mode"),
            new Entry("Settings",           "settings"),
            new Entry("Keyboard shortcuts", "keyboard-shortcuts"),
            new Entry("Tag management",     "tag-management"),
            new Entry("Git sync",           "git-sync"),
            new Entry("Cloud sync",         "cloud-sync"),

            new SectionHeader("Superpower"),
            new Entry("AI assistant",       "ai-assistant"),

            new SectionHeader("Background"),
            new Entry("GTD basics",         "gtd")
    );

    private final JEditorPane mainPane;
    private final JEditorPane sidePane;
    private final JSplitPane  contentSplit;
    private final JButton     popOutButton;

    public HelpPanel() {
        super(new BorderLayout());

        mainPane = makeEditorPane();
        sidePane = makeEditorPane();

        var mainScroll = new JScrollPane(mainPane);
        mainScroll.setBorder(null);

        var sideScroll = new JScrollPane(sidePane);
        sideScroll.setBorder(null);

        contentSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mainScroll, sideScroll);
        contentSplit.setResizeWeight(1.0);
        contentSplit.setBorder(null);
        contentSplit.setDividerSize(4);

        var listModel = new DefaultListModel<Item>();
        ITEMS.forEach(listModel::addElement);
        var conceptList = new JList<>(listModel);
        conceptList.setCellRenderer(new ItemRenderer());
        conceptList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        conceptList.setBorder(new EmptyBorder(0, 0, 4, 0));
        conceptList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            var selected = conceptList.getSelectedValue();
            if (selected instanceof Entry entry) loadMain(entry.slug());
            else if (selected instanceof SectionHeader) conceptList.clearSelection();
        });

        var listScroll = new JScrollPane(conceptList);
        listScroll.setPreferredSize(new Dimension(160, 0));
        listScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1,
                UIManager.getColor("Separator.foreground")));
        listScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        popOutButton = new JButton(new FlatSVGIcon(HelpPanel.class.getResource("/icons/external-link.svg")).derive(14, 14));
        popOutButton.setToolTipText("Open help in floating window");
        popOutButton.setBorderPainted(false);
        popOutButton.setContentAreaFilled(false);
        popOutButton.setFocusPainted(false);
        popOutButton.setVisible(false);

        var header = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                UIManager.getColor("Separator.foreground")));
        header.add(popOutButton);

        add(header,       BorderLayout.NORTH);
        add(listScroll,   BorderLayout.WEST);
        add(contentSplit, BorderLayout.CENTER);

        SwingUtilities.invokeLater(() -> contentSplit.setDividerLocation(1.0));

        // Select "Getting started" — first Entry after the opening header
        for (int i = 0; i < listModel.size(); i++) {
            if (listModel.get(i) instanceof Entry) { conceptList.setSelectedIndex(i); break; }
        }
    }

    public void setOnPopOut(Runnable r) {
        for (var l : popOutButton.getActionListeners()) popOutButton.removeActionListener(l);
        if (r != null) popOutButton.addActionListener(e -> r.run());
        popOutButton.setVisible(r != null);
    }

    private JEditorPane makeEditorPane() {
        var kit = new HTMLEditorKit();
        applyStyles(kit.getStyleSheet());
        var pane = new JEditorPane();
        pane.setEditorKit(kit);
        pane.setEditable(false);
        pane.setContentType("text/html");
        pane.addHyperlinkListener(this::handleLink);
        return pane;
    }

    private static void applyStyles(StyleSheet css) {
        css.addRule("body { font-family: sans-serif; margin: 16px; line-height: 1.5; font-size: 13px; }");
        css.addRule("h2   { font-size: 16px; margin-top: 0; }");
        css.addRule("h3   { font-size: 14px; margin-top: 14px; }");
        css.addRule("h4   { font-size: 13px; margin-top: 10px; }");
        css.addRule("p    { margin-top: 4px; margin-bottom: 8px; }");
        css.addRule("ul   { margin-top: 4px; margin-bottom: 8px; }");
        css.addRule("li   { margin-bottom: 3px; }");
        css.addRule("a    { color: #5b9bd5; }");
        css.addRule("code { font-size: 12px; }");
    }

    private void loadMain(String slug) {
        var path = slug.contains("/")
                ? "/resources/help/" + slug + ".html"
                : "/resources/help/concepts/" + slug + ".html";
        var url = HelpPanel.class.getResource(path);
        if (url == null) {
            mainPane.setText(errorHtml("No article for \"" + slug + "\"."));
        } else {
            try {
                mainPane.setPage(url);
                mainPane.setCaretPosition(0);
            } catch (IOException e) {
                mainPane.setText(errorHtml("Failed to load article."));
            }
        }
        SwingUtilities.invokeLater(() -> contentSplit.setDividerLocation(1.0));
    }

    private void loadSide(String slug) {
        var resourcePath = "/resources/help/concepts/" + slug + ".html";
        var url = HelpPanel.class.getResource(resourcePath);
        if (url == null) {
            sidePane.setText(errorHtml("No article for \"" + slug + "\"."));
        } else {
            try {
                sidePane.setPage(url);
                sidePane.setCaretPosition(0);
            } catch (IOException e) {
                sidePane.setText(errorHtml("Failed to load article."));
            }
        }
        SwingUtilities.invokeLater(() -> {
            int total = contentSplit.getWidth();
            if (total > 0) contentSplit.setDividerLocation((int) (total * 0.65));
        });
    }

    private void handleLink(HyperlinkEvent e) {
        if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
        var url  = e.getURL();
        var desc = e.getDescription();
        if (desc != null && desc.startsWith("concept://")) {
            loadSide(desc.substring("concept://".length()));
        } else if (url != null && (url.getProtocol().equals("http") || url.getProtocol().equals("https"))) {
            try { Desktop.getDesktop().browse(url.toURI()); } catch (Exception ignored) {}
        }
    }

    private static String errorHtml(String msg) {
        return "<html><body style='font-family:sans-serif;margin:16px'><p><i>" + msg + "</i></p></body></html>";
    }

    private static final class ItemRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            if (value instanceof SectionHeader h) {
                var label = new JLabel(h.label().toUpperCase());
                label.setFont(label.getFont().deriveFont(Font.BOLD, 9.5f));
                label.setForeground(UIManager.getColor("Label.disabledForeground"));
                label.setOpaque(true);
                label.setBackground(list.getBackground());
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(index == 0 ? 0 : 1, 0, 0, 0,
                                UIManager.getColor("Separator.foreground")),
                        BorderFactory.createEmptyBorder(8, 6, 2, 4)));
                return label;
            }
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Entry entry) setText(entry.title());
            setBorder(BorderFactory.createEmptyBorder(2, 14, 2, 4));
            return this;
        }
    }
}
