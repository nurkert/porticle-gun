package eu.nurkert.porticlegun.config;

import eu.nurkert.porticlegun.PorticleGun;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public final class ConfigManager {

    private static final int DEFAULT_MAX_TARGET_DISTANCE = 128;
    private static final int DEFAULT_MAX_PLAYER_DISTANCE = 100;
    private static final int DEFAULT_MAX_BLOCK_TRACE = 100;
    private static final boolean DEFAULT_GRAVITY_GUN_ENABLED = true;
    private static final Set<Material> DEFAULT_GRAVITY_GUN_BLOCK_BLACKLIST =
            Collections.unmodifiableSet(EnumSet.of(Material.AIR, Material.CHEST));

    private static int maxTargetDistance = DEFAULT_MAX_TARGET_DISTANCE;
    private static int maxPlayerDistance = DEFAULT_MAX_PLAYER_DISTANCE;
    private static int maxBlockTrace = DEFAULT_MAX_BLOCK_TRACE;
    private static boolean gravityGunEnabled = DEFAULT_GRAVITY_GUN_ENABLED;
    private static final Set<Material> gravityGunBlockBlacklist = EnumSet.copyOf(DEFAULT_GRAVITY_GUN_BLOCK_BLACKLIST);

    private ConfigManager() {
    }

    public static void init() {
        load(PorticleGun.getInstance().getConfig());
    }

    public static void reload() {
        PorticleGun plugin = PorticleGun.getInstance();
        plugin.reloadConfig();
        load(plugin.getConfig());
    }

    private static void load(FileConfiguration config) {
        maxTargetDistance = ensurePositive(config.getInt("portal.max-target-distance", DEFAULT_MAX_TARGET_DISTANCE), DEFAULT_MAX_TARGET_DISTANCE);
        maxPlayerDistance = ensurePositive(config.getInt("portal.max-player-distance", DEFAULT_MAX_PLAYER_DISTANCE), DEFAULT_MAX_PLAYER_DISTANCE);
        maxBlockTrace = ensurePositive(config.getInt("portal.max-block-trace", DEFAULT_MAX_BLOCK_TRACE), DEFAULT_MAX_BLOCK_TRACE);
        gravityGunEnabled = config.getBoolean("gravity-gun.enabled", DEFAULT_GRAVITY_GUN_ENABLED);
        loadGravityGunBlacklist(config.getStringList("gravity-gun.block-blacklist"));
    }

    private static void loadGravityGunBlacklist(Collection<String> configuredEntries) {
        gravityGunBlockBlacklist.clear();

        if (configuredEntries == null || configuredEntries.isEmpty()) {
            gravityGunBlockBlacklist.addAll(DEFAULT_GRAVITY_GUN_BLOCK_BLACKLIST);
            return;
        }

        configuredEntries.stream()
                .map(entry -> entry == null ? null : entry.trim())
                .filter(entry -> entry != null && !entry.isEmpty())
                .forEach(entry -> {
                    Material material = Material.matchMaterial(entry);
                    if (material == null) {
                        material = Material.matchMaterial(entry.toUpperCase(Locale.ROOT));
                    }
                    if (material != null) {
                        gravityGunBlockBlacklist.add(material);
                    }
                });

        if (gravityGunBlockBlacklist.isEmpty()) {
            gravityGunBlockBlacklist.addAll(DEFAULT_GRAVITY_GUN_BLOCK_BLACKLIST);
        }
    }

    private static int ensurePositive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    public static int getPortalMaxTargetDistance() {
        return maxTargetDistance;
    }

    public static int getPortalMaxPlayerDistance() {
        return maxPlayerDistance;
    }

    public static int getPortalMaxBlockTrace() {
        return maxBlockTrace;
    }

    public static boolean isGravityGunEnabled() {
        return gravityGunEnabled;
    }

    public static Set<Material> getGravityGunBlockBlacklist() {
        return EnumSet.copyOf(gravityGunBlockBlacklist);
    }
}
