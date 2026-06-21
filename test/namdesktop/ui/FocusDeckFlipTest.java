package namdesktop.ui;

import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import namdesktop.persist.JsonWorkspaceRepository;
import namdesktop.service.NamWorkspaceService;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import java.awt.Container;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The focus deck shows a re-triage button only when a flip target is wired (#400). */
class FocusDeckFlipTest {

    private MoonCardPanel deck(NodeStatus flipTarget, String label) {
        var ws  = NamWorkspace.createDefault();
        var svc = new NamWorkspaceService(ws, new JsonWorkspaceRepository(),
                Path.of(System.getProperty("java.io.tmpdir"), "focus-flip-test.json"));
        var cards = List.<MoonCardPanel.Card>of(
                new MoonCardPanel.Card(UUID.randomUUID(), "Task", null, List.of()));
        return flipTarget == null
                ? new MoonCardPanel(cards, svc, () -> {}, id -> {})
                : new MoonCardPanel(cards, svc, () -> {}, id -> {}, flipTarget, label);
    }

    private boolean hasButton(Container c, String textFragment) {
        for (var comp : c.getComponents()) {
            if (comp instanceof JButton b && b.getText() != null && b.getText().contains(textFragment))
                return true;
            if (comp instanceof Container child && hasButton(child, textFragment))
                return true;
        }
        return false;
    }

    @Test
    void flipButtonShown_whenTargetWired() {
        assertTrue(hasButton(deck(NodeStatus.BACKLOG, "Backlog"), "Move to Backlog"));
        assertTrue(hasButton(deck(NodeStatus.NEXT, "Next"), "Move to Next"));
    }

    @Test
    void noFlipButton_whenUnwired() {
        assertFalse(hasButton(deck(null, null), "Move to"));
    }
}
