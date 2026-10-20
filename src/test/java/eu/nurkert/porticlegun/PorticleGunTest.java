package eu.nurkert.porticlegun;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PorticleGunTest {

    @Test
    void acceptsMinimumAndNewerVersions() {
        assertTrue(PorticleGun.isSupportedServerVersion("1.21.4-R0.1-SNAPSHOT"));
        assertTrue(PorticleGun.isSupportedServerVersion("1.21.10-R0.1-SNAPSHOT"));
        assertTrue(PorticleGun.isSupportedServerVersion("1.22-R0.1-SNAPSHOT"));
        assertTrue(PorticleGun.isSupportedServerVersion("2.0"));
    }

    @Test
    void rejectsOlderVersions() {
        assertFalse(PorticleGun.isSupportedServerVersion("1.21.3-R0.1-SNAPSHOT"));
        assertFalse(PorticleGun.isSupportedServerVersion("1.21-R0.1-SNAPSHOT"));
        assertFalse(PorticleGun.isSupportedServerVersion("1.20.6-R0.1-SNAPSHOT"));
    }
}
