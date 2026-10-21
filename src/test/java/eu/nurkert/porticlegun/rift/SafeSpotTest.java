package eu.nurkert.porticlegun.rift;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.junit.jupiter.api.Test;

class SafeSpotTest {

    private static boolean safe(Material ground, Material feet, Material head, Environment environment, int y) {
        return SafeSpot.isSafe(
                ground,
                feet,
                head,
                ground != Material.AIR && ground != Material.WATER && ground != Material.LAVA,
                feet == Material.AIR || feet == Material.SHORT_GRASS,
                head == Material.AIR,
                false,
                environment,
                y);
    }

    @Test
    void grassWithAirAboveIsSafe() {
        assertTrue(safe(Material.GRASS_BLOCK, Material.AIR, Material.AIR, Environment.NORMAL, 70));
        assertTrue(safe(Material.STONE, Material.SHORT_GRASS, Material.AIR, Environment.NORMAL, 70));
    }

    @Test
    void hazardsAndLiquidsAreNotSafe() {
        assertFalse(safe(Material.MAGMA_BLOCK, Material.AIR, Material.AIR, Environment.NETHER, 40));
        assertFalse(safe(Material.CACTUS, Material.AIR, Material.AIR, Environment.NORMAL, 70));
        assertFalse(safe(Material.WATER, Material.AIR, Material.AIR, Environment.NORMAL, 62));
        assertFalse(safe(Material.LAVA, Material.AIR, Material.AIR, Environment.NETHER, 31));
    }

    @Test
    void needsRoomForBodyAndHead() {
        assertFalse(safe(Material.STONE, Material.STONE, Material.AIR, Environment.NORMAL, 70));
        assertFalse(safe(Material.STONE, Material.AIR, Material.STONE, Environment.NORMAL, 70));
    }

    @Test
    void lavaNextToTheSpotIsNotSafe() {
        assertFalse(SafeSpot.isSafe(
                Material.NETHERRACK, Material.AIR, Material.AIR, true, true, true, true, Environment.NETHER, 40));
    }

    @Test
    void theNetherRoofIsNotSafe() {
        assertFalse(safe(Material.NETHERRACK, Material.AIR, Material.AIR, Environment.NETHER, SafeSpot.NETHER_ROOF));
        assertFalse(safe(Material.BEDROCK, Material.AIR, Material.AIR, Environment.NETHER, 60));
        assertTrue(safe(Material.NETHERRACK, Material.AIR, Material.AIR, Environment.NETHER, 60));
    }
}
