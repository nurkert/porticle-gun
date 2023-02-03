package eu.nurkert.porticlegun.portals;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

public class PortalTracing {

    /**
     * Traces a portal from the player's current view.
     *
     * @param player the player to trace the portal from.
     * @return the potential portal, or null if the player is not targeting a (valid) block.
     */
    public static PotentialPortal tracePortal(Player player) {
        Block block = player.getTargetBlockExact(128);
        // get the mid point of the block
        BlockFace face = getBlockFace(player);
        if(face == null) return null;
        Vector direction = face.getDirection().normalize();
        Location loc = block.getLocation().add(direction);
        if(loc.distance(player.getLocation()) >= 100) return null;
        return new PotentialPortal(loc, direction);
    }

    /**
     * Gets the BlockFace of the block the player is currently targeting.
     *
     * @param player the player's whos targeted blocks BlockFace is to be checked.
     * @return the BlockFace of the targeted block, or null if the targeted block is non-occluding.
     */
    public static BlockFace getBlockFace(Player player) {
        List<Block> lastTwoTargetBlocks = player.getLastTwoTargetBlocks(null, 100);
        if (lastTwoTargetBlocks.size() != 2 || !lastTwoTargetBlocks.get(1).getType().isOccluding()) return null;
        Block targetBlock = lastTwoTargetBlocks.get(1);
        Block adjacentBlock = lastTwoTargetBlocks.get(0);
        return targetBlock.getFace(adjacentBlock);
    }
}
