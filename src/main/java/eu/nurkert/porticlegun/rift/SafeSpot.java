package eu.nurkert.porticlegun.rift;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Decides whether a player can safely land on a spot: solid ground that does not hurt, two free blocks for body and
 * head, no lava next to it, and in the nether not on the bedrock roof.
 */
public final class SafeSpot {

    /** Blocks that hurt or trap when standing on or in them. */
    static final Set<Material> HAZARDS = EnumSet.of(
            Material.LAVA,
            Material.MAGMA_BLOCK,
            Material.CACTUS,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE,
            Material.POWDER_SNOW,
            Material.COBWEB,
            Material.POINTED_DRIPSTONE,
            Material.END_PORTAL,
            Material.NETHER_PORTAL,
            Material.END_GATEWAY);

    /** The nether's bedrock roof starts at y 123; spots above it are on the roof. */
    static final int NETHER_ROOF = 123;

    private SafeSpot() {}

    /**
     * @param ground the block the player would stand on
     * @param feet the block at the player's feet
     * @param head the block at the player's head
     * @param groundSolid whether the ground block is solid
     * @param feetPassable whether the player can stand in the feet block
     * @param headPassable whether the head block is free
     * @param lavaNearby whether lava touches the spot horizontally
     * @param feetY the y coordinate of the feet block
     */
    public static boolean isSafe(
            Material ground,
            Material feet,
            Material head,
            boolean groundSolid,
            boolean feetPassable,
            boolean headPassable,
            boolean lavaNearby,
            World.Environment environment,
            int feetY) {
        if (!groundSolid || HAZARDS.contains(ground) || isLiquid(ground)) {
            return false;
        }
        if (!feetPassable || !headPassable || HAZARDS.contains(feet) || HAZARDS.contains(head)) {
            return false;
        }
        if (isLiquid(feet) || isLiquid(head) || lavaNearby) {
            return false;
        }
        if (environment == World.Environment.NETHER && (feetY >= NETHER_ROOF || ground == Material.BEDROCK)) {
            return false;
        }
        return true;
    }

    static boolean isLiquid(Material material) {
        return material == Material.WATER || material == Material.LAVA || material == Material.BUBBLE_COLUMN;
    }
}
