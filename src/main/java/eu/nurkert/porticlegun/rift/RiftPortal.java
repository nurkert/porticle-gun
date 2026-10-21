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

    final UUID owner;
    final Location feet;
    final Vector normal;
    final Vector right;
    RiftPortal partner;
    long openedAt;
    long closedAt;

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
        if (point.getWorld() == null || !point.getWorld().equals(feet.getWorld())) {
            return false;
        }
        Vector relative = point.toVector().subtract(centre().toVector());
        double depth = relative.dot(normal);
        double side = relative.dot(right) / HALF_WIDTH;
        double up = relative.getY() / (HEIGHT / 2);
        return Math.abs(depth) < 0.45 && side * side + up * up <= 1.0;
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
