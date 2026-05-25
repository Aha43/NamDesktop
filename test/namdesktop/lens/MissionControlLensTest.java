package namdesktop.lens;

import namdesktop.model.MissionControl;
import namdesktop.model.NamNode;
import namdesktop.model.NamWorkspace;
import namdesktop.model.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MissionControlLensTest {

    private NamWorkspace workspace;
    private MissionControlLens lens;

    @BeforeEach
    void setUp() {
        workspace = NamWorkspace.createDefault();
        lens = new MissionControlLens();
    }

    // helpers

    private UUID addProject(String title, String... tags) {
        var node = new NamNode(UUID.randomUUID(), title);
        node.setProject(true);
        for (var tag : tags) node.getTags().add(tag);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(workspace.getProjectsNodeId()).orElseThrow().getChildIds().add(node.getId());
        return node.getId();
    }

    private UUID addSubProject(UUID parentId, String title, String... tags) {
        var node = new NamNode(UUID.randomUUID(), title);
        node.setProject(true);
        for (var tag : tags) node.getTags().add(tag);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(parentId).orElseThrow().getChildIds().add(node.getId());
        return node.getId();
    }

    private UUID addAction(UUID parentId, String title, NodeStatus status) {
        var node = new NamNode(UUID.randomUUID(), title);
        node.setStatus(status);
        workspace.getNodes().put(node.getId(), node);
        workspace.getNode(parentId).orElseThrow().getChildIds().add(node.getId());
        return node.getId();
    }

    private MissionControl mc(String... tags) {
        return new MissionControl("Test MC", List.of(tags));
    }

    // --- basic cases ---

    @Test
    void noTaggedProjects_returnsEmptyList() {
        addProject("Untagged project");
        assertTrue(lens.stations(mc("@goal"), workspace).isEmpty());
    }

    @Test
    void singleTaggedProject_returnsOneStation() {
        addProject("Retirement", "@goal");
        var stations = lens.stations(mc("@goal"), workspace);
        assertEquals(1, stations.size());
        assertEquals("Retirement", stations.get(0).title());
    }

    @Test
    void multipleTaggedProjects_returnsAllAsStations() {
        addProject("Retirement", "@goal");
        addProject("Health", "@goal");
        var stations = lens.stations(mc("@goal"), workspace);
        assertEquals(2, stations.size());
    }

    @Test
    void stationsAreReturnedAlphabetically() {
        addProject("Zebra", "@goal");
        addProject("Alpha", "@goal");
        var stations = lens.stations(mc("@goal"), workspace);
        assertEquals("Alpha", stations.get(0).title());
        assertEquals("Zebra", stations.get(1).title());
    }

    // --- multi-tag OR logic ---

    @Test
    void multiTag_projectCarryingEitherTagBecomesStation() {
        addProject("Retirement", "@retirement");
        addProject("Health fund", "@financial-independence");
        addProject("Unrelated", "@other");
        var stations = lens.stations(mc("@retirement", "@financial-independence"), workspace);
        assertEquals(2, stations.size());
    }

    @Test
    void multiTag_projectCarryingBothTagsCountedOnce() {
        addProject("Overlap", "@retirement", "@financial-independence");
        var stations = lens.stations(mc("@retirement", "@financial-independence"), workspace);
        assertEquals(1, stations.size());
    }

    // --- deduplication ---

    @Test
    void dedup_childTaggedUnderTaggedParent_onlyParentIsStation() {
        var parentId = addProject("Financial Planning", "@retirement");
        addSubProject(parentId, "Pension Fund", "@retirement");
        var stations = lens.stations(mc("@retirement"), workspace);
        assertEquals(1, stations.size());
        assertEquals("Financial Planning", stations.get(0).title());
    }

    @Test
    void dedup_rolledUpCount_reflectsDiscardedTaggedChildren() {
        var parentId = addProject("Financial Planning", "@retirement");
        addSubProject(parentId, "Pension Fund", "@retirement");
        addSubProject(parentId, "ISA", "@retirement");
        var stations = lens.stations(mc("@retirement"), workspace);
        assertEquals(2, stations.get(0).rolledUpCount());
    }

    @Test
    void dedup_deepNesting_onlyRootKept() {
        var rootId  = addProject("A", "@goal");
        var midId   = addSubProject(rootId, "B", "@goal");
        addSubProject(midId, "C", "@goal");
        var stations = lens.stations(mc("@goal"), workspace);
        assertEquals(1, stations.size());
        assertEquals("A", stations.get(0).title());
        assertEquals(2, stations.get(0).rolledUpCount());
    }

    @Test
    void dedup_siblingBranchesNotAffected() {
        addProject("Branch A", "@goal");
        addProject("Branch B", "@goal");
        var stations = lens.stations(mc("@goal"), workspace);
        assertEquals(2, stations.size());
    }

    // --- stats ---

    @Test
    void stats_subProjectCount_countsDescendantProjects() {
        var rootId = addProject("Root", "@goal");
        addSubProject(rootId, "Sub A");
        addSubProject(rootId, "Sub B");
        var station = lens.stations(mc("@goal"), workspace).get(0);
        assertEquals(2, station.subProjectCount());
    }

    @Test
    void stats_totalActions_countsNonProjectDescendants() {
        var rootId = addProject("Root", "@goal");
        addAction(rootId, "Action 1", NodeStatus.NEXT);
        addAction(rootId, "Action 2", NodeStatus.BACKLOG);
        var station = lens.stations(mc("@goal"), workspace).get(0);
        assertEquals(2, station.totalActions());
    }

    @Test
    void stats_doneCount_countsOnlyDoneActions() {
        var rootId = addProject("Root", "@goal");
        addAction(rootId, "Done action", NodeStatus.DONE);
        addAction(rootId, "Next action", NodeStatus.NEXT);
        var station = lens.stations(mc("@goal"), workspace).get(0);
        assertEquals(1, station.doneCount());
        assertEquals(2, station.totalActions());
    }

    @Test
    void stats_doneRatio_zeroWhenNoActions() {
        addProject("Empty", "@goal");
        var station = lens.stations(mc("@goal"), workspace).get(0);
        assertEquals(0.0, station.doneRatio());
    }

    @Test
    void stats_doneRatio_correctFraction() {
        var rootId = addProject("Root", "@goal");
        addAction(rootId, "Done 1", NodeStatus.DONE);
        addAction(rootId, "Done 2", NodeStatus.DONE);
        addAction(rootId, "Next",   NodeStatus.NEXT);
        var station = lens.stations(mc("@goal"), workspace).get(0);
        assertEquals(2.0 / 3.0, station.doneRatio(), 1e-9);
    }

    @Test
    void stats_maxDepth_zeroForLeafStation() {
        addProject("Leaf", "@goal");
        var station = lens.stations(mc("@goal"), workspace).get(0);
        assertEquals(0, station.maxDepth());
    }

    @Test
    void stats_maxDepth_oneForDirectChildren() {
        var rootId = addProject("Root", "@goal");
        addSubProject(rootId, "Child");
        var station = lens.stations(mc("@goal"), workspace).get(0);
        assertEquals(1, station.maxDepth());
    }

    @Test
    void stats_maxDepth_reflectsDeepestBranch() {
        var rootId  = addProject("Root", "@goal");
        var midId   = addSubProject(rootId, "Mid");
        addSubProject(midId, "Deep");
        addSubProject(rootId, "Shallow");
        var station = lens.stations(mc("@goal"), workspace).get(0);
        assertEquals(2, station.maxDepth());
    }

    @Test
    void nonProjectNodeWithTag_notIncludedAsStation() {
        var action = new NamNode(UUID.randomUUID(), "Tagged action");
        action.getTags().add("@goal");
        workspace.getNodes().put(action.getId(), action);
        workspace.getNode(workspace.getNextActionsNodeId()).orElseThrow().getChildIds().add(action.getId());
        assertTrue(lens.stations(mc("@goal"), workspace).isEmpty());
    }
}
