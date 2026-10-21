package eu.nurkert.porticlegun.rift;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RiftSettingsTest {

    @Test
    void defaultsWithoutConfig() {
        RiftSettings settings = RiftSettings.load(new YamlConfiguration());
        assertEquals(8, settings.tankCapacity());
        assertEquals(4, settings.riftsPerFluid());
        assertEquals(4, settings.startingCharges());
        assertEquals(30 * 20, settings.portalTicks());
        assertEquals(5, settings.homes());
    }

    @Test
    void tankLevelFollowsTheCharge() {
        RiftSettings settings = RiftSettings.load(new YamlConfiguration());
        assertEquals(0, settings.tankLevel(0));
        assertEquals(1, settings.tankLevel(1));
        assertEquals(1, settings.tankLevel(2));
        assertEquals(2, settings.tankLevel(3));
        assertEquals(4, settings.tankLevel(8));
        assertEquals(4, settings.tankLevel(99));
    }

    @Test
    void invalidValuesAreClamped() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("interdimensional-gun.tank-capacity", 0);
        config.set("interdimensional-gun.starting-charges", 50);
        config.set("interdimensional-gun.homes", 20);
        config.set("interdimensional-gun.drops.enderman-chance", 7);
        RiftSettings settings = RiftSettings.load(config);
        assertEquals(1, settings.tankCapacity());
        assertEquals(1, settings.startingCharges());
        assertEquals(7, settings.homes());
        assertEquals(1.0, settings.endermanChance());
    }

    @Test
    void tankBarShowsFilledAndEmptyCells() {
        assertEquals("§a■§a■§8■", RiftItems.tankBar(2, 3));
    }
}
