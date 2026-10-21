package eu.nurkert.porticlegun.rift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RiftDestinationTest {

    @Test
    void everyDestinationSurvivesSerialisation() {
        for (RiftDestination destination : new RiftDestination[] {
            RiftDestination.world("world_nether"),
            RiftDestination.spawn("world"),
            RiftDestination.home(3),
            RiftDestination.back(),
            RiftDestination.death(),
            RiftDestination.structure(RiftStructure.ANCIENT_CITY)
        }) {
            assertEquals(destination, RiftDestination.parse(destination.serialize()));
        }
    }

    @Test
    void worldNamesMayContainColons() {
        assertEquals("my:world", RiftDestination.parse("world:my:world").world());
    }

    @Test
    void invalidTextIsRejected() {
        assertNull(RiftDestination.parse(null));
        assertNull(RiftDestination.parse(""));
        assertNull(RiftDestination.parse("world:"));
        assertNull(RiftDestination.parse("home:-1"));
        assertNull(RiftDestination.parse("home:abc"));
        assertNull(RiftDestination.parse("somewhere"));
        assertNull(RiftDestination.parse("structure:atlantis"));
    }
}
