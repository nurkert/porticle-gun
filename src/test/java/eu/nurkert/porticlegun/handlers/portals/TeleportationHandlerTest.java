package eu.nurkert.porticlegun.handlers.portals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class TeleportationHandlerTest {

    private static final double EPSILON = 1e-9;

    // portal directions point out of the wall the portal sits on
    private static final List<Vector> DIRECTIONS = List.of(
            new Vector(1, 0, 0),
            new Vector(-1, 0, 0),
            new Vector(0, 0, 1),
            new Vector(0, 0, -1),
            new Vector(0, 1, 0),
            new Vector(0, -1, 0));

    private static Vector transform(Vector velocity, Vector from, Vector to) {
        return TeleportationHandler.transformVector(
                velocity, TeleportationHandler.createPortalBasis(from), TeleportationHandler.createPortalBasis(to));
    }

    @Test
    void basisIsOrthonormalForEveryDirection() {
        for (Vector direction : DIRECTIONS) {
            TeleportationHandler.PortalBasis basis = TeleportationHandler.createPortalBasis(direction);
            assertEquals(1, basis.forward.length(), EPSILON);
            assertEquals(1, basis.up.length(), EPSILON);
            assertEquals(1, basis.right.length(), EPSILON);
            assertEquals(0, basis.forward.dot(basis.up), EPSILON);
            assertEquals(0, basis.forward.dot(basis.right), EPSILON);
            assertEquals(0, basis.up.dot(basis.right), EPSILON);
        }
    }

    @Test
    void enteringVelocityLeavesTheDestinationPortal() {
        for (Vector from : DIRECTIONS) {
            for (Vector to : DIRECTIONS) {
                // moving into the source wall at speed 2
                Vector entering = from.clone().multiply(-2);
                Vector leaving = transform(entering, from, to);
                assertEquals(2, leaving.dot(to), EPSILON, "from " + from + " to " + to);
            }
        }
    }

    @Test
    void speedIsPreserved() {
        Vector velocity = new Vector(-0.7, 0.3, 0.25);
        for (Vector from : DIRECTIONS) {
            for (Vector to : DIRECTIONS) {
                assertEquals(velocity.length(), transform(velocity, from, to).length(), EPSILON);
            }
        }
    }

    @Test
    void verticalMotionIsKeptBetweenWallPortals() {
        Vector leaving = transform(new Vector(-1, 0.5, 0), new Vector(1, 0, 0), new Vector(0, 0, 1));
        assertEquals(0.5, leaving.getY(), EPSILON);
        assertEquals(1, leaving.getZ(), EPSILON);
    }

    @Test
    void fallingIntoFloorPortalShootsOutOfWallPortal() {
        Vector leaving = transform(new Vector(0, -1.5, 0), new Vector(0, 1, 0), new Vector(-1, 0, 0));
        assertEquals(-1.5, leaving.getX(), EPSILON);
        assertEquals(0, leaving.getY(), EPSILON);
    }

    @Test
    void fallingIntoFloorPortalShootsUpOutOfAnotherFloorPortal() {
        Vector leaving = transform(new Vector(0, -1, 0), new Vector(0, 1, 0), new Vector(0, 1, 0));
        assertEquals(1, leaving.getY(), EPSILON);
    }
}
