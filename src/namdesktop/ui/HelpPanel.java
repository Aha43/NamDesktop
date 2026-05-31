package namdesktop.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.io.IOException;
import java.util.List;

public final class HelpPanel extends JPanel {

    private record Concept(String title, String slug) {}

    private static final List<Concept> CONCEPTS = List.of(
            new Concept("Getting started",   "tutorials/getting-started"),
            new Concept("GTD basics",        "gtd"),
            new Concept("Inbox",             "inbox"),
            new Concept("Next Actions",      "next-actions"),
            new Concept("Backlog",           "backlog"),
            new Concept("Projects",          "projects"),
            new Concept("Project Workbench", "workbench"),
            new Concept("Tag filter",        "contexts"),
            new Concept("Goal Board",        "mission-control"),
            new Concept("Focus mode",        "focus-mode"),
            new Concept("Git sync",          "git-sync"),
            new Concept("AI assistant",      "ai-assistant")
    );

    private final JEditorPane mainPane;
    private final JEditorPane sidePane;
    private final JSplitPane  contentSplit;

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

        var listModel = new DefaultListModel<Concept>();
        CONCEPTS.forEach(listModel::addElement);
        var conceptList = new JList<>(listModel);
        conceptList.setCellRenderer(new ConceptRenderer());
        conceptList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        conceptList.setBorder(new EmptyBorder(4, 4, 4, 4));
        conceptList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            var c = conceptList.getSelectedValue();
            if (c != null) loadMain(c.slug());
        });

        var listScroll = new JScrollPane(conceptList);
        listScroll.setPreferredSize(new Dimension(160, 0));
        listScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1,
                UIManager.getColor("Separator.foreground")));
        listScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(listScroll,  BorderLayout.WEST);
        add(contentSplit, BorderLayout.CENTER);

        SwingUtilities.invokeLater(() -> contentSplit.setDividerLocation(1.0));

        conceptList.setSelectedIndex(0);
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

    private static final class ConceptRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Concept c) setText(c.title());
            return this;
        }
    }
}
