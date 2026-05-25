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
    public double doneRatio() {
        return totalActions == 0 ? 0.0 : (double) doneCount / totalActions;
    }
}
