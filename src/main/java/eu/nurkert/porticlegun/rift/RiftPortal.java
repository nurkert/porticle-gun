package eu.nurkert.porticlegun.rift;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * One side of a rift: a vertical oval, two blocks wide and a bit more than two high, standing on {@code feet} and
 * facing {@code normal}. Entities stepping through it come out in front of its partner.
 */
final class RiftPortal {

    static final double HALF_WIDTH = 1.0;
    static final double HEIGHT = 2.4;
    // how far in front of or behind its plane a body still counts as standing in the portal
    static final double DEPTH = 0.5;
    // half a player's width: the body goes through as soon as it touches the oval, not only with its centre
    static final double MARGIN = 0.3;

    final UUID owner;
    final Location feet;
    final Vector normal;
    final Vector right;
    RiftPortal partner;
    long openedAt;
    long closedAt;
    // a charging entrance (shown while the destination is prepared) collapsing because nothing was found
    boolean charging;

    RiftPortal(UUID owner, Location feet, Vector normal) {
        this.owner = owner;
        this.feet = feet.clone();
        this.normal = normal.clone().setY(0).normalize();
        this.right = new Vector(-this.normal.getZ(), 0, this.normal.getX());
    }

    Location centre() {
        return feet.clone().add(0, HEIGHT / 2, 0);
    }

    /**
     * @return whether a point (an entity's centre) is inside the portal's oval, close to its plane
     */
    boolean contains(Location point) {
        return crossedBy(point, point);
    }

    /**
     * Whether a body moving between two points (its centre) went through the portal: it stands in it, or it passed the
     * portal's plane between them, however fast. A body touching the oval with its side counts as well.
     *
     * @return whether the move took the body through the oval
     */
    boolean crossedBy(Location from, Location to) {
        if (to.getWorld() == null
                || !to.getWorld().equals(feet.getWorld())
                || !to.getWorld().equals(from.getWorld())) {
            return false;
        }
        Vector centre = centre().toVector();
        Vector a = from.toVector().subtract(centre);
        Vector b = to.toVector().subtract(centre);
        double depthA = a.dot(normal);
        double depthB = b.dot(normal);
        Vector at;
        if (Math.abs(depthB) < DEPTH) {
            at = b;
        } else if (depthA * depthB < 0 && a.distanceSquared(b) < 16) {
            // the point where the move passed the plane
            at = a.add(b.clone().subtract(a).multiply(depthA / (depthA - depthB)));
        } else {
            return false;
        }
        double side = at.dot(right) / (HALF_WIDTH + MARGIN);
        double up = at.getY() / (HEIGHT / 2 + MARGIN);
        return side * side + up * up <= 1.0;
    }

    /**
     * @return where an entity coming through the partner portal appears: in front of this portal, facing away from it
     */
    Location exit() {
        Location exit = feet.clone().add(normal.clone().multiply(1.1));
        exit.setDirection(normal);
        exit.setPitch(0);
        return exit;
    }
}
