package eu.nurkert.porticlegun.rift;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * The {@code interdimensional-gun} section of config.yml.
 */
public record RiftSettings(
        boolean enabled,
        int tankCapacity,
        int riftsPerFluid,
        int startingCharges,
        long cooldownMillis,
        int portalTicks,
        double closeDistance,
        int homes,
        int randomRadius,
        int netherRadius,
        int endRadius,
        int searchAttempts,
        Set<String> disabledWorlds,
        double endStoneChance,
        double endStoneFortuneBonus,
        double endermanChance,
        double endermanLootingBonus,
        long compassMillis,
        boolean attunedFreeRifts,
        int structureSearchRadius) {

    public static RiftSettings load(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("interdimensional-gun");
        if (section == null) {
            section = config.createSection("interdimensional-gun");
        }
        int capacity = Math.max(1, section.getInt("tank-capacity", 8));
        List<String> disabled = section.getStringList("disabled-worlds");
        return new RiftSettings(
                section.getBoolean("enabled", true),
                capacity,
                Math.max(1, section.getInt("rifts-per-fluid", 4)),
                Math.max(0, Math.min(capacity, section.getInt("starting-charges", 4))),
                Math.max(0, Math.round(section.getDouble("cooldown-seconds", 3) * 1000)),
                Math.max(40, (int) Math.round(section.getDouble("portal-seconds", 30) * 20)),
                Math.max(3, section.getDouble("close-distance", 24)),
                Math.max(0, Math.min(7, section.getInt("homes", 5))),
                Math.max(16, section.getInt("random-radius", 3000)),
                Math.max(16, section.getInt("nether-radius", 1000)),
                Math.max(16, section.getInt("end-radius", 1500)),
                Math.max(1, section.getInt("search-attempts", 40)),
                new HashSet<>(disabled),
                clampChance(section.getDouble("drops.end-stone-chance", 0.03)),
                clampChance(section.getDouble("drops.end-stone-fortune-bonus", 0.01)),
                clampChance(section.getDouble("drops.enderman-chance", 0.08)),
                clampChance(section.getDouble("drops.enderman-looting-bonus", 0.03)),
                Math.max(1000, Math.round(section.getDouble("compass-minutes", 10) * 60_000)),
                section.getBoolean("attuned-free-rifts", true),
                Math.max(8, Math.min(200, section.getInt("structure-search-radius", 100))));
    }

    private static double clampChance(double value) {
        return Math.max(0, Math.min(1, value));
    }

    /**
     * @return the tank level shown by the model, 0 (empty) to 4 (full)
     */
    public int tankLevel(int charges) {
        if (charges <= 0) {
            return 0;
        }
        return Math.min(4, (int) Math.ceil(4.0 * charges / tankCapacity));
    }
}
