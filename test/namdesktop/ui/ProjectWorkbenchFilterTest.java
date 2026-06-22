package namdesktop.ui;

import namdesktop.lens.ChildSection;
import namdesktop.model.NamNode;
import namdesktop.model.NodeStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Covers the tag + archive filters applied to the embedded board (top-level Columns/Readiness views). */
class ProjectWorkbenchFilterTest {

    private ChildSection section(String title, String... tags) {
        var node = new NamNode(UUID.randomUUID(), title);
        node.setProject(true);
        node.setTags(List.of(tags));
        return new ChildSection(node, List.of());
    }

    private ChildSection archivedSection(String title) {
        var s = section(title);
        s.project().setStatus(NodeStatus.ARCHIVED);
        return s;
    }

    @Test
    void filterArchived_dropsArchivedByDefault() {
        var active = section("Active");
        var result = ProjectWorkbenchPanel.filterArchivedSections(List.of(active, archivedSection("Old")), false);
        assertEquals(List.of(active), result);
    }

    @Test
    void filterArchived_keepsArchivedWhenShown() {
        var all = List.of(section("Active"), archivedSection("Old"));
        assertSame(all, ProjectWorkbenchPanel.filterArchivedSections(all, true));
    }

    @Test
    void emptyFilter_returnsAllSections() {
        var all = List.of(section("A", "work"), section("B", "home"));
        assertSame(all, ProjectWorkbenchPanel.filterSections(all, Set.of()));
    }

    @Test
    void filter_keepsSectionsWithAnyMatchingTag() {
        var work = section("Work", "work");
        var home = section("Home", "home");
        var both = section("Both", "work", "home");

        var result = ProjectWorkbenchPanel.filterSections(List.of(work, home, both), Set.of("work"));

        assertEquals(List.of(work, both), result);
    }

    @Test
    void filter_isOrAcrossSelectedTags() {
        var work = section("Work", "work");
        var home = section("Home", "home");
        var other = section("Other", "finance");

        var result = ProjectWorkbenchPanel.filterSections(
                List.of(work, home, other), Set.of("work", "home"));

        assertEquals(List.of(work, home), result);
    }

    @Test
    void filter_dropsSectionsWithNoMatch() {
        var result = ProjectWorkbenchPanel.filterSections(
                List.of(section("A", "work")), Set.of("home"));
        assertTrue(result.isEmpty());
    }
}
