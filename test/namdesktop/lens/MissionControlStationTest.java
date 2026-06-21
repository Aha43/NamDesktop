package namdesktop.lens;

import namdesktop.lens.MissionControlStation.HeatLevel;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MissionControlStationTest {

    private static MissionControlStation station(int done, int total) {
        return new MissionControlStation(UUID.randomUUID(), "S", 0, 0, done, total, 0);
    }

    @Test
    void noActions_isNeutral_notRed() {
        assertEquals(HeatLevel.NEUTRAL, station(0, 0).heatLevel());
    }

    @Test
    void noneDone_withActions_isRed() {
        assertEquals(HeatLevel.RED, station(0, 4).heatLevel());
    }

    @Test
    void belowOneThird_isRed() {
        assertEquals(HeatLevel.RED, station(8, 25).heatLevel()); // 0.32
    }

    @Test
    void atAmberThreshold_isAmber() {
        assertEquals(HeatLevel.AMBER, station(1, 3).heatLevel()); // 0.333… ≥ 0.33
    }

    @Test
    void midRange_isAmber() {
        assertEquals(HeatLevel.AMBER, station(1, 2).heatLevel()); // 0.5
    }

    @Test
    void justBelowGreenThreshold_isAmber() {
        assertEquals(HeatLevel.AMBER, station(66, 100).heatLevel()); // 0.66 < 0.67
    }

    @Test
    void atGreenThreshold_isGreen() {
        assertEquals(HeatLevel.GREEN, station(67, 100).heatLevel()); // 0.67
    }

    @Test
    void allDone_isGreen() {
        assertEquals(HeatLevel.GREEN, station(5, 5).heatLevel());
    }
}
