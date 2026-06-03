package namdesktop.ui;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AgeFormatterTest {

    @Test
    void ageDays_returnsNullWhenBothNull() {
        assertNull(UiHelper.ageDays(null, null));
    }

    @Test
    void ageDays_usesUpdatedAtWhenBothPresent() {
        var updated = LocalDateTime.now().minusDays(3);
        var created = LocalDateTime.now().minusDays(10);
        assertEquals(3L, UiHelper.ageDays(updated, created));
    }

    @Test
    void ageDays_fallsBackToCreatedAtWhenUpdatedAtNull() {
        var created = LocalDateTime.now().minusDays(5);
        assertEquals(5L, UiHelper.ageDays(null, created));
    }

    @Test
    void ageDays_usesUpdatedAtWhenCreatedAtNull() {
        var updated = LocalDateTime.now().minusDays(2);
        assertEquals(2L, UiHelper.ageDays(updated, null));
    }

    @Test
    void ageText_returnsEmptyForNull() {
        assertEquals("", UiHelper.ageText(null));
    }

    @Test
    void ageText_formatsDays() {
        assertEquals("0d", UiHelper.ageText(0L));
        assertEquals("6d", UiHelper.ageText(6L));
    }

    @Test
    void ageText_formatsWeeks() {
        assertEquals("1w", UiHelper.ageText(7L));
        assertEquals("4w", UiHelper.ageText(29L));
    }

    @Test
    void ageText_formatsMonths() {
        assertEquals("1m", UiHelper.ageText(30L));
        assertEquals("12m", UiHelper.ageText(364L));
    }

    @Test
    void ageText_formatsYears() {
        assertEquals("1y", UiHelper.ageText(365L));
        assertEquals("2y", UiHelper.ageText(730L));
    }
}
