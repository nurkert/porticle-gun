package eu.nurkert.porticlegun.config;

import eu.nurkert.porticlegun.visualization.PortalColor;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reads config.yml: portal distances, gravity gun settings and custom portal colours.
 */
public final class ConfigManager {

    private static final int DEFAULT_MAX_TARGET_DISTANCE = 128;
    private static final int DEFAULT_MAX_PLAYER_DISTANCE = 100;
    private static final int DEFAULT_MAX_BLOCK_TRACE = 100;
    private static final boolean DEFAULT_GRAVITY_GUN_ENABLED = true;
    private static final boolean DEFAULT_GRAVITY_GUN_ALLOW_PLAYER_CAPTURE = false;
    private static final boolean DEFAULT_GRAVITY_GUN_ALLOW_CHEST_CAPTURE = true;
    private static final Set<Material> DEFAULT_GRAVITY_GUN_BLOCK_BLACKLIST =
            Collections.unmodifiableSet(EnumSet.of(Material.AIR));

    private int maxTargetDistance = DEFAULT_MAX_TARGET_DISTANCE;
    private int maxPlayerDistance = DEFAULT_MAX_PLAYER_DISTANCE;
    private int maxBlockTrace = DEFAULT_MAX_BLOCK_TRACE;
    private boolean gravityGunEnabled = DEFAULT_GRAVITY_GUN_ENABLED;
    private boolean gravityGunAllowPlayerCapture = DEFAULT_GRAVITY_GUN_ALLOW_PLAYER_CAPTURE;
    private final Set<Material> gravityGunBlockBlacklist = EnumSet.copyOf(DEFAULT_GRAVITY_GUN_BLOCK_BLACKLIST);

    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load(plugin.getConfig());
    }

    public void reload() {
        plugin.reloadConfig();
        load(plugin.getConfig());
    }

    private void load(FileConfiguration config) {
        maxTargetDistance = ensurePositive(
                config.getInt("portal.max-target-distance", DEFAULT_MAX_TARGET_DISTANCE), DEFAULT_MAX_TARGET_DISTANCE);
        maxPlayerDistance = ensurePositive(
                config.getInt("portal.max-player-distance", DEFAULT_MAX_PLAYER_DISTANCE), DEFAULT_MAX_PLAYER_DISTANCE);
        maxBlockTrace = ensurePositive(
                config.getInt("portal.max-block-trace", DEFAULT_MAX_BLOCK_TRACE), DEFAULT_MAX_BLOCK_TRACE);
        gravityGunEnabled = config.getBoolean("gravity-gun.enabled", DEFAULT_GRAVITY_GUN_ENABLED);
        gravityGunAllowPlayerCapture =
                config.getBoolean("gravity-gun.allow-player-capture", DEFAULT_GRAVITY_GUN_ALLOW_PLAYER_CAPTURE);
        boolean allowChestCapture =
                config.getBoolean("gravity-gun.allow-chest-capture", DEFAULT_GRAVITY_GUN_ALLOW_CHEST_CAPTURE);
        loadGravityGunBlacklist(config.getStringList("gravity-gun.block-blacklist"), allowChestCapture);
        loadCustomPortalColors(config);
    }

    private void loadGravityGunBlacklist(Collection<String> configuredEntries, boolean allowChestCapture) {
        gravityGunBlockBlacklist.clear();

        if (configuredEntries == null || configuredEntries.isEmpty()) {
            gravityGunBlockBlacklist.addAll(DEFAULT_GRAVITY_GUN_BLOCK_BLACKLIST);
            if (!allowChestCapture) {
                gravityGunBlockBlacklist.add(Material.CHEST);
            }
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

        if (allowChestCapture) {
            gravityGunBlockBlacklist.remove(Material.CHEST);
        } else {
            gravityGunBlockBlacklist.add(Material.CHEST);
        }
    }

    private void loadCustomPortalColors(FileConfiguration config) {
        PortalColor.reloadCustomColors(config.getMapList("portal.custom-colors"), plugin.getLogger());
    }

    private int ensurePositive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    public int getPortalMaxTargetDistance() {
        return maxTargetDistance;
    }

    public int getPortalMaxPlayerDistance() {
        return maxPlayerDistance;
    }

    public int getPortalMaxBlockTrace() {
        return maxBlockTrace;
    }

    public boolean isGravityGunEnabled() {
        return gravityGunEnabled;
    }

    public boolean isGravityGunPlayerCaptureEnabled() {
        return gravityGunAllowPlayerCapture;
    }

    public Set<Material> getGravityGunBlockBlacklist() {
        return EnumSet.copyOf(gravityGunBlockBlacklist);
    }
}
