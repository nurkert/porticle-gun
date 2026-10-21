package eu.nurkert.porticlegun.visualization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PortalHudTest {

    private static String strip(String line) {
        // drop the hex colour codes: §x§r§r§g§g§b§b
        return line.replaceAll("§x(§[0-9a-fA-F]){6}", "");
    }

    @Test
    void showsOpenAndClosedRingsInOrder() {
        String line = PortalHud.build(0xFFA500, true, 0x0000AA, false, false);
        assertEquals("" + PortalHud.LEAD + PortalHud.LEFT_OPEN + PortalHud.GAP + PortalHud.RIGHT_CLOSED, strip(line));
    }

    @Test
    void colourEachRingWithItsPortalColour() {
        String line = PortalHud.build(0xFFA500, false, 0x0000AA, true, false);
        assertTrue(line.startsWith(PortalHud.LEAD + "§x§F§F§A§5§0§0" + PortalHud.LEFT_CLOSED));
        assertTrue(line.endsWith("§x§0§0§0§0§A§A" + PortalHud.RIGHT_OPEN));
    }

    @Test
    void bedrockUsesPlainSpacesInsteadOfTheJavaSpaceGlyph() {
        String line = PortalHud.build(0xFFFFFF, false, 0xFFFFFF, false, true);
        assertFalse(line.contains(String.valueOf(PortalHud.GAP)));
        assertEquals("" + PortalHud.LEFT_CLOSED + "  " + PortalHud.RIGHT_CLOSED, strip(line));
    }
}
