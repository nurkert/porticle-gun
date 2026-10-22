package eu.nurkert.porticlegun.rift;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * Decides where the two portals of a rift stand, so they appear where a player would expect them.
 *
 * <ul>
 *   <li>Both portals stand on the block grid, facing a compass direction and filling exactly two blocks.
 *   <li>The entrance stands in front of the player, on the ground if there is ground, closer if a wall is near, and
 *       floating in the air when the player stands at an edge.
 *   <li>The exit stands behind the landing spot, turned so that travellers step out with room in front of them
 *       instead of facing a wall, preferably in the direction they walked in.
 *   <li>Stored destinations (homes, back, death, spawn) that are no longer safe move to the nearest safe spot.
 * </ul>
 */
final class RiftPlacement {

    /** Where a portal stands (feet in the middle of its bottom edge) and which way it faces. */
    record Spot(Location feet, Vector normal) {}

    private static final double[] ENTRANCE_DISTANCES = {2.2, 1.7, 2.8, 1.3};
    private static final int[] ENTRANCE_HEIGHTS = {0, 1, -1, -2};
    private static final double EXIT_BEHIND = 1.1;
    private static final int NEARBY_RADIUS = 3;

    private RiftPlacement() {}

    /**
     * @return where the entrance stands, facing the player, or {@code null} if there is no room in front of them
     */
    static Spot entrance(Location player, Vector direction) {
        Vector facing = cardinal(direction);
        Vector normal = facing.clone().multiply(-1);
        Spot floating = null;
        for (double distance : ENTRANCE_DISTANCES) {
            Location base = player.clone().add(facing.clone().multiply(distance));
            for (int height : ENTRANCE_HEIGHTS) {
                Location feet = onGrid(base, normal, player.getBlockY() + height);
                if (!hasRoom(feet, normal)) {
                    continue;
                }
                if (standsOnGround(feet, normal)) {
                    return new Spot(feet, normal);
                }
                if (height == 0 && floating == null) {
                    floating = new Spot(feet, normal);
                }
            }
        }
        return floating;
    }

    /**
     * @param landing where travellers should appear
     * @param preferred the direction the player walked into the entrance
     * @return the exit portal: behind the landing spot, facing the best of the four compass directions
     */
    static Spot exit(Location landing, Vector preferred) {
        Vector walked = cardinal(preferred);
        Vector best = walked;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 2 * i;
            Vector direction = new Vector(-Math.round(Math.sin(angle)), 0, Math.round(Math.cos(angle)));
            Location portalFeet = onGrid(
                    landing.clone().subtract(direction.clone().multiply(EXIT_BEHIND)), direction, landing.getBlockY());
            int score = (hasRoom(portalFeet, direction) ? 6 : 0) + openAhead(landing, direction);
            if (direction.equals(walked)) {
                score += 1;
            }
            if (score > bestScore) {
                bestScore = score;
                best = direction;
            }
        }
        return new Spot(
                onGrid(landing.clone().subtract(best.clone().multiply(EXIT_BEHIND)), best, landing.getBlockY()), best);
    }

    /**
     * @return whether there is solid ground under at least one of the two blocks the portal fills
     */
    private static boolean standsOnGround(Location feet, Vector normal) {
        Vector right = new Vector(-normal.getZ(), 0, normal.getX());
        for (double side : new double[] {-0.5, 0.5}) {
            if (feet.clone()
                    .add(right.clone().multiply(side))
                    .add(0, -0.5, 0)
                    .getBlock()
                    .getType()
                    .isSolid()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the compass direction (north, east, south or west) closest to a horizontal direction
     */
    static Vector cardinal(Vector direction) {
        if (Math.abs(direction.getX()) > Math.abs(direction.getZ())) {
            return new Vector(Math.signum(direction.getX()), 0, 0);
        }
        return new Vector(0, 0, direction.getZ() < 0 ? -1 : 1);
    }

    /**
     * Puts a portal on the block grid: in the middle of a block along its facing, and with its two block wide face
     * centred on the edge between two blocks, so it fills exactly two blocks.
     */
    static Location onGrid(Location near, Vector normal, int y) {
        Location feet = near.clone();
        feet.setY(y);
        if (normal.getX() != 0) {
            feet.setX(Math.floor(near.getX()) + 0.5);
            feet.setZ(Math.round(near.getZ()));
        } else {
            feet.setZ(Math.floor(near.getZ()) + 0.5);
            feet.setX(Math.round(near.getX()));
        }
        return feet;
    }

    /**
     * @return the stored destination, or the nearest safe spot within a few blocks if it is no longer safe
     */
    static Location refine(Location target) {
        Block feet = target.getBlock();
        Location exact = SafeLocationFinder.spotAbove(feet.getRelative(BlockFace.DOWN));
        if (exact != null) {
            exact.setYaw(target.getYaw());
            return exact;
        }
        Location nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int dx = -NEARBY_RADIUS; dx <= NEARBY_RADIUS; dx++) {
            for (int dz = -NEARBY_RADIUS; dz <= NEARBY_RADIUS; dz++) {
                for (int dy = -NEARBY_RADIUS; dy <= NEARBY_RADIUS; dy++) {
                    Location spot = SafeLocationFinder.spotAbove(feet.getRelative(dx, dy - 1, dz));
                    if (spot == null) {
                        continue;
                    }
                    double distance = dx * dx + dy * dy * 2 + dz * dz; // prefer staying on the same level
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = spot;
                    }
                }
            }
        }
        return nearest != null ? nearest : target;
    }

    /**
     * @return whether a portal fits: free space across its width and up to its top
     */
    static boolean hasRoom(Location feet, Vector normal) {
        Vector right = new Vector(-normal.getZ(), 0, normal.getX());
        // the two blocks the portal fills (on the grid its centre is the edge between them)
        for (double side : new double[] {-0.5, 0.5}) {
            for (double up : new double[] {0.3, 1.2, 2.1}) {
                Block block = feet.clone()
                        .add(right.clone().multiply(side))
                        .add(0, up, 0)
                        .getBlock();
                if (!block.isPassable()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * @return how many blocks in front of the landing spot are free at body and head height (0 to 4)
     */
    private static int openAhead(Location landing, Vector direction) {
        int free = 0;
        for (int step = 1; step <= 4; step++) {
            Location ahead = landing.clone().add(direction.clone().multiply(step));
            if (!ahead.getBlock().isPassable()
                    || !ahead.clone().add(0, 1, 0).getBlock().isPassable()) {
                break;
            }
            free++;
        }
        return free;
    }
}
