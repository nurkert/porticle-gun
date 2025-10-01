package eu.nurkert.porticlegun.handlers.portals;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import eu.nurkert.porticlegun.handlers.visualization.concrete.PortalVisualizationType;
import eu.nurkert.porticlegun.portals.Portal;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleportationHandlerTest {

    private ServerMock server;
    private WorldMock world;
    private TeleportationHandler handler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        handler = new TeleportationHandler();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void preservesMomentumAcrossPerpendicularWallPortals() {
        Portal source = new Portal(new Location(world, 0, 64, 0), new Vector(0, 0, 1), "gun", Portal.PortalType.PRIMARY, PortalVisualizationType.RECTANGULAR);
        Portal destinationPortal = new Portal(new Location(world, 10, 64, 0), new Vector(1, 0, 0), "gun", Portal.PortalType.SECONDARY, PortalVisualizationType.RECTANGULAR);
        ActivePortalsHandler.setPrimaryPortal("gun", source);
        ActivePortalsHandler.setSecondaryPortal("gun", destinationPortal);

        PlayerMock player = server.addPlayer();

        Vector initialVelocity = new Vector(0.4, 0.6, 0.8);
        Vector initialLook = new Vector(0.2, 0.4, 0.9);

        Location startingLocation = new Location(world, -1, 64, 0.5);
        startingLocation.setDirection(initialLook);
        player.teleport(startingLocation);
        player.setVelocity(initialVelocity);

        Location destinationLocation = new Location(world, 0.5, 64, 0.5);
        destinationLocation.setDirection(initialLook);
        PlayerMoveEvent event = new PlayerMoveEvent(player, startingLocation, destinationLocation);

        Vector expectedVelocity = new Vector(0.8, 0.6, -0.4);
        Vector expectedLook = new Vector(0.9, 0.4, -0.2).normalize();
        float expectedYaw = computeYaw(expectedLook);
        float expectedPitch = computePitch(expectedLook);

        handler.on(event);

        assertVectorEquals(expectedVelocity, player.getVelocity());
        assertVectorEquals(expectedLook, player.getLocation().getDirection());
        assertEquals(expectedYaw, player.getLocation().getYaw(), 1.0e-4);
        assertEquals(expectedPitch, player.getLocation().getPitch(), 1.0e-4);

        ActivePortalsHandler.removePrimaryPortal("gun");
        ActivePortalsHandler.removeSecondaryPortal("gun");
    }

    @Test
    void preservesMomentumAcrossFloorToWallPortal() {
        Portal source = new Portal(new Location(world, 5, 63, 5), new Vector(0, 1, 0), "gun2", Portal.PortalType.PRIMARY, PortalVisualizationType.RECTANGULAR);
        Portal destinationPortal = new Portal(new Location(world, 5, 70, 15), new Vector(0, 0, 1), "gun2", Portal.PortalType.SECONDARY, PortalVisualizationType.RECTANGULAR);
        ActivePortalsHandler.setPrimaryPortal("gun2", source);
        ActivePortalsHandler.setSecondaryPortal("gun2", destinationPortal);

        PlayerMock player = server.addPlayer();

        Vector initialVelocity = new Vector(0.3, 1.2, -0.5);
        Vector initialLook = new Vector(0.2, 1.0, -0.3);

        Location startingLocation = new Location(world, 5.5, 62, 5.5);
        startingLocation.setDirection(initialLook);
        player.teleport(startingLocation);
        player.setVelocity(initialVelocity);

        Location destinationLocation = new Location(world, 5.5, 63, 5.5);
        destinationLocation.setDirection(initialLook);
        PlayerMoveEvent event = new PlayerMoveEvent(player, startingLocation, destinationLocation);

        Vector expectedVelocity = new Vector(-0.3, -0.5, 1.2);
        Vector expectedLook = new Vector(-0.2, -0.3, 1.0).normalize();
        float expectedYaw = computeYaw(expectedLook);
        float expectedPitch = computePitch(expectedLook);

        handler.on(event);

        assertVectorEquals(expectedVelocity, player.getVelocity());
        assertVectorEquals(expectedLook, player.getLocation().getDirection());
        assertEquals(expectedYaw, player.getLocation().getYaw(), 1.0e-4);
        assertEquals(expectedPitch, player.getLocation().getPitch(), 1.0e-4);

        ActivePortalsHandler.removePrimaryPortal("gun2");
        ActivePortalsHandler.removeSecondaryPortal("gun2");
    }

    private void assertVectorEquals(Vector expected, Vector actual) {
        assertEquals(expected.getX(), actual.getX(), 1.0e-6);
        assertEquals(expected.getY(), actual.getY(), 1.0e-6);
        assertEquals(expected.getZ(), actual.getZ(), 1.0e-6);
    }

    private float computeYaw(Vector direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
    }

    private float computePitch(Vector direction) {
        return (float) Math.toDegrees(Math.asin(-direction.getY()));
    }
}
