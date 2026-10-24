package eu.nurkert.porticlegun.rift;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class RiftPortalTest {

    // a world that is only ever compared with itself
    private static final World WORLD = (World) Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[] {World.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> null;
            });

    // feet at the origin, facing +z; a player's centre walks at y 0.9
    private final RiftPortal portal = new RiftPortal(UUID.randomUUID(), at(0, 0, 0), new Vector(0, 0, 1));

    private static Location at(double x, double y, double z) {
        return new Location(WORLD, x, y, z);
    }

    private boolean move(double x, double fromZ, double toZ) {
        return portal.crossedBy(at(x, 0.9, fromZ), at(x, 0.9, toZ));
    }

    @Test
    void walkingThroughCounts() {
        assertTrue(move(0, -0.3, -0.1));
        assertTrue(move(0, -0.6, -0.35));
    }

    @Test
    void aMoveJumpingOverThePlaneCounts() {
        // a sprint jump covers more than the portal is deep in one move
        assertTrue(move(0, -0.6, 0.6));
        assertTrue(move(0.5, 0.7, -0.7));
    }

    @Test
    void touchingTheRimWithTheBodyCounts() {
        assertTrue(move(1.15, -0.6, 0.6));
        assertTrue(portal.contains(at(0, 2.6, 0)));
    }

    @Test
    void passingBesideOrInFrontDoesNot() {
        assertFalse(move(1.7, -0.6, 0.6));
        assertFalse(portal.crossedBy(at(-1, 0.9, 0.8), at(1, 0.9, 0.8)));
        assertFalse(move(0, -1.2, -0.7));
        assertFalse(portal.contains(at(0, 3.2, 0)));
    }

    @Test
    void aTeleportAcrossThePlaneDoesNot() {
        assertFalse(move(0, -5, 5));
    }
}
