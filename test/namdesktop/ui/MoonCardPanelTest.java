package namdesktop.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The focus deck's advance-after-removal logic (#418), shared by both Mark-done and the re-triage flip.
 * With cards A,B,C: removing the current card should show the next one, wrapping to the first when the
 * last card is removed (circular Next), never moving backward.
 */
class MoonCardPanelTest {

    @Test
    void removingFirstCard_showsNewFirst() {
        // [A,B,C] remove index 0 → remaining 2 → index 0 (old B)
        assertEquals(0, MoonCardPanel.indexAfterRemoval(0, 2));
    }

    @Test
    void removingMiddleCard_showsFollowing() {
        // [A,B,C] remove index 1 → remaining 2 → index 1 (old C)
        assertEquals(1, MoonCardPanel.indexAfterRemoval(1, 2));
    }

    @Test
    void removingLastCard_wrapsToFirst() {
        // [A,B,C] remove index 2 → remaining 2 → index 0 (wrap to A), not 1 (backward)
        assertEquals(0, MoonCardPanel.indexAfterRemoval(2, 2));
    }

    @Test
    void removingSoleCard_isZero() {
        // [A] remove index 0 → remaining 0 → index 0 (empty state)
        assertEquals(0, MoonCardPanel.indexAfterRemoval(0, 0));
    }
}
