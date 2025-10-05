package eu.nurkert.porticlegun.portals;

import eu.nurkert.porticlegun.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class PortalTracing {

    private static final Set<Material> TRANSPARENT_BLOCKS = createTransparentBlockSet();

    /**
     * Traces a portal from the player's current view.
     *
     * @param player the player to trace the portal from.
     * @return the potential portal, or null if the player is not targeting a (valid) block.
     */
    public static PotentialPortal tracePortal(Player player) {
        Block block = getTargetBlockIgnoringFoliage(player);
        if (block == null) {
            return null;
        }
        // get the mid point of the block
        BlockFace face = getBlockFace(player);
        if(face == null) return null;
        Vector direction = face.getDirection().normalize();
        Location loc = block.getLocation().add(direction);
        if(loc.distance(player.getLocation()) >= ConfigManager.getPortalMaxPlayerDistance()) return null;
        return new PotentialPortal(loc, direction);
    }

    /**
     * Gets the BlockFace of the block the player is currently targeting.
     *
     * @param player the player's whos targeted blocks BlockFace is to be checked.
     * @return the BlockFace of the targeted block, or null if the targeted block is non-occluding.
     */
    public static BlockFace getBlockFace(Player player) {
        List<Block> lastTwoTargetBlocks = player.getLastTwoTargetBlocks(TRANSPARENT_BLOCKS, ConfigManager.getPortalMaxBlockTrace());
        if (lastTwoTargetBlocks.size() != 2 || !lastTwoTargetBlocks.get(1).getType().isOccluding()) return null;
        Block targetBlock = lastTwoTargetBlocks.get(1);
        Block adjacentBlock = lastTwoTargetBlocks.get(0);
        return targetBlock.getFace(adjacentBlock);
    }

    private static Block getTargetBlockIgnoringFoliage(Player player) {
        int maxDistance = ConfigManager.getPortalMaxTargetDistance();
        Location eyeLocation = player.getEyeLocation();
        BlockIterator iterator = new BlockIterator(player.getWorld(), eyeLocation.toVector(), eyeLocation.getDirection(), 0, maxDistance);

        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (!TRANSPARENT_BLOCKS.contains(block.getType())) {
                return block;
            }
        }

        return null;
    }

    private static Set<Material> createTransparentBlockSet() {
        EnumSet<Material> transparentBlocks = EnumSet.of(
                Material.AIR,
                Material.CAVE_AIR,
                Material.VOID_AIR,
                Material.SHORT_GRASS,
                Material.TALL_GRASS,
                Material.FERN,
                Material.LARGE_FERN,
                Material.VINE,
                Material.GLOW_LICHEN,
                Material.HANGING_ROOTS
        );
        transparentBlocks.addAll(Tag.LEAVES.getValues());
        transparentBlocks.addAll(Tag.SMALL_FLOWERS.getValues());
        transparentBlocks.addAll(Tag.SAPLINGS.getValues());
        return transparentBlocks;
    }
}
