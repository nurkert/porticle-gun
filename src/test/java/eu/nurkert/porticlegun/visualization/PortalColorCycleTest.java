package eu.nurkert.porticlegun.visualization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PortalColorCycleTest {

    @AfterEach
    void reset() {
        PortalColor.unregisterCustomColors();
    }

    @Test
    void previousIsNextBackwards() {
        for (PortalColor color : PortalColor.values()) {
            assertEquals(color.getName(), color.next().previous().getName());
            assertEquals(color.getName(), color.previous().next().getName());
        }
    }

    @Test
    void goingRoundBackwardsVisitsTheSameColoursInReverse() {
        List<String> forwards = new ArrayList<>();
        List<String> backwards = new ArrayList<>();
        PortalColor up = PortalColor.GOLD;
        PortalColor down = PortalColor.GOLD;
        for (int i = 0; i < PortalColor.values().length; i++) {
            forwards.add(up.getName());
            backwards.add(0, down.getName());
            up = up.next();
            down = down.previous();
        }
        // forwards: GOLD, a, b, ..., z; backwards read in reverse: a, b, ..., z, GOLD shifted by one
        backwards.add(0, backwards.remove(backwards.size() - 1));
        assertEquals(forwards, backwards);
    }

    @Test
    void aColourFromBeforeAReloadStillStepsByOne() {
        PortalColor before = PortalColor.registerCustom("Mint", "§a", Color.fromRGB(0x3EB489), DyeColor.LIME);
        PortalColor expectedNext = before.next();
        PortalColor expectedPrevious = before.previous();
        PortalColor.unregisterCustomColors();
        PortalColor after = PortalColor.registerCustom("Mint", "§a", Color.fromRGB(0x3EB489), DyeColor.LIME);
        assertNotSame(before, after);
        assertEquals(expectedNext.getName(), before.next().getName());
        assertEquals(expectedPrevious.getName(), before.previous().getName());
    }
}
