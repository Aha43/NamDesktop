package namdesktop.lens;

import java.util.UUID;

public record MissionControlStation(
        UUID id,
        String title,
        int subProjectCount,
        int maxDepth,
        int doneCount,
        int totalActions,
        int rolledUpCount
) {
    /** Heat-map color band for a station's progress. NEUTRAL means "no actions yet". */
    public enum HeatLevel { NEUTRAL, RED, AMBER, GREEN }

    public double doneRatio() {
        return totalActions == 0 ? 0.0 : (double) doneCount / totalActions;
    }

    /**
     * Heat band for this station. A station with no actions is {@link HeatLevel#NEUTRAL}
     * ("no actions yet") rather than red — an empty project has made no progress, but it
     * is not behind. Otherwise: green ≥ 67%, amber ≥ 33%, red below.
     */
    public HeatLevel heatLevel() {
        if (totalActions == 0) return HeatLevel.NEUTRAL;
        var ratio = doneRatio();
        if (ratio >= 0.67) return HeatLevel.GREEN;
        if (ratio >= 0.33) return HeatLevel.AMBER;
        return HeatLevel.RED;
    }
}
