package namdesktop.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.io.IOException;
import java.net.URL;
import java.util.List;

public final class HelpPanel extends JPanel {

    private record Tutorial(String title, String resource) {}

    private static final List<Tutorial> TUTORIALS = List.of(
            new Tutorial("Getting started",           "/resources/help/tutorials/getting-started.html"),
            new Tutorial("Planning a goal with Readiness view",  "/resources/help/tutorials/planning-a-goal-with-mcr.html")
    );

    private final JEditorPane tutorialPane;
    private final JEditorPane conceptPane;
    private final JSplitPane  contentSplit;

    public HelpPanel() {
        super(new BorderLayout());

        tutorialPane = makeEditorPane();
        conceptPane  = makeEditorPane();

        var tutorialScroll = new JScrollPane(tutorialPane);
        tutorialScroll.setBorder(null);

        var conceptScroll = new JScrollPane(conceptPane);
        conceptScroll.setBorder(null);

        contentSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tutorialScroll, conceptScroll);
        contentSplit.setResizeWeight(1.0);
        contentSplit.setBorder(null);
        contentSplit.setDividerSize(4);

        var listModel = new DefaultListModel<Tutorial>();
        TUTORIALS.forEach(listModel::addElement);
        var tutorialList = new JList<>(listModel);
        tutorialList.setCellRenderer(new TutorialRenderer());
        tutorialList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tutorialList.setBorder(new EmptyBorder(4, 4, 4, 4));
        tutorialList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            var t = tutorialList.getSelectedValue();
            if (t != null) loadTutorial(t.resource());
        });

        var listScroll = new JScrollPane(tutorialList);
        listScroll.setPreferredSize(new Dimension(190, 0));
        listScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1,
                UIManager.getColor("Separator.foreground")));

        add(listScroll,   BorderLayout.WEST);
        add(contentSplit, BorderLayout.CENTER);

        // Concept pane starts hidden (no divider needed until content arrives)
        SwingUtilities.invokeLater(() -> contentSplit.setDividerLocation(1.0));

        tutorialList.setSelectedIndex(0);
    }

    private JEditorPane makeEditorPane() {
        var kit = new HTMLEditorKit();
        var css = kit.getStyleSheet();
        applyStyles(css);

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

    private void loadTutorial(String resourcePath) {
        var url = HelpPanel.class.getResource(resourcePath);
        if (url == null) {
            tutorialPane.setText("<html><body style='font-family:sans-serif;margin:16px'>"
                    + "<p><i>Content not found: " + resourcePath + "</i></p></body></html>");
            return;
        }
        try {
            tutorialPane.setPage(url);
            tutorialPane.setCaretPosition(0);
        } catch (IOException e) {
            tutorialPane.setText("<html><body style='font-family:sans-serif;margin:16px'>"
                    + "<p><i>Failed to load content.</i></p></body></html>");
        }
        // Collapse concept pane when loading a new tutorial
        SwingUtilities.invokeLater(() -> contentSplit.setDividerLocation(1.0));
    }

    private void loadConcept(String slug) {
        var resourcePath = "/resources/help/concepts/" + slug + ".html";
        var url = HelpPanel.class.getResource(resourcePath);
        if (url == null) {
            conceptPane.setText("<html><body style='font-family:sans-serif;margin:16px'>"
                    + "<p><i>No article for \"" + slug + "\".</i></p></body></html>");
        } else {
            try {
                conceptPane.setPage(url);
                conceptPane.setCaretPosition(0);
            } catch (IOException e) {
                conceptPane.setText("<html><body style='font-family:sans-serif;margin:16px'>"
                        + "<p><i>Failed to load concept.</i></p></body></html>");
            }
        }
        // Reveal concept pane at ~35% of total width
        SwingUtilities.invokeLater(() -> {
            int total = contentSplit.getWidth();
            if (total > 0) contentSplit.setDividerLocation((int) (total * 0.65));
        });
    }

    private void handleLink(HyperlinkEvent e) {
        if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
        var url = e.getURL();
        // concept:// links have no URL object — check description
        var desc = e.getDescription();
        if (desc != null && desc.startsWith("concept://")) {
            loadConcept(desc.substring("concept://".length()));
        } else if (url != null && (url.getProtocol().equals("http") || url.getProtocol().equals("https"))) {
            try { Desktop.getDesktop().browse(url.toURI()); } catch (Exception ignored) {}
        }
    }

    private static final class TutorialRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Tutorial t) setText(t.title());
            return this;
        }
    }
}
