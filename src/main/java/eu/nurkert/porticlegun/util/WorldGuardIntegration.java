package eu.nurkert.porticlegun.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.logging.Level;

public final class WorldGuardIntegration {

    private static boolean enabled;
    private static WorldGuardPlugin worldGuardPlugin;
    private static RegionContainer regionContainer;
    private static StateFlag portalCreateFlag;
    private static StateFlag portalUseFlag;
    private static StateFlag gravityGunFlag;

    private WorldGuardIntegration() {
    }

    public static void init(JavaPlugin plugin) {
        Plugin wgPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (!(wgPlugin instanceof WorldGuardPlugin wg) || !wgPlugin.isEnabled()) {
            plugin.getLogger().log(Level.INFO, "WorldGuard not present - skipping integration.");
            return;
        }

        try {
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            portalCreateFlag = registerStateFlag(registry, "porticlegun-portal-create", true);
            portalUseFlag = registerStateFlag(registry, "porticlegun-portal-use", true);
            gravityGunFlag = registerStateFlag(registry, "porticlegun-gravity-gun", true);

            worldGuardPlugin = wg;
            regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
            enabled = true;

            plugin.getLogger().log(Level.INFO, "Enabled WorldGuard support with PorticleGun flags.");
        } catch (FlagConflictException conflict) {
            plugin.getLogger().log(Level.WARNING, "A conflicting WorldGuard flag prevented PorticleGun integration: {0}",
                    conflict.getMessage());
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialise WorldGuard integration", throwable);
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean canCreatePortal(Player player, Location baseLocation, Vector direction) {
        if (!enabled) {
            return true;
        }

        if (player == null || baseLocation == null) {
            return false;
        }

        LocalPlayer localPlayer = worldGuardPlugin.wrapPlayer(player);
        if (!testFlag(localPlayer, baseLocation, portalCreateFlag)) {
            return false;
        }

        if (direction == null) {
            return true;
        }

        Location upper = baseLocation.clone().add(0, 1, 0);
        if (Math.abs(direction.getY()) < 0.5 && !testFlag(localPlayer, upper, portalCreateFlag)) {
            return false;
        }

        return true;
    }

    public static boolean canUsePortal(Entity entity, Location source, Location destination) {
        if (!enabled) {
            return true;
        }

        LocalPlayer localPlayer = entity instanceof Player ? worldGuardPlugin.wrapPlayer((Player) entity) : null;
        return testFlag(localPlayer, source, portalUseFlag) && testFlag(localPlayer, destination, portalUseFlag);
    }

    public static boolean canUseGravityGun(Player player, Location location) {
        if (!enabled) {
            return true;
        }

        if (player == null) {
            return false;
        }

        return testFlag(worldGuardPlugin.wrapPlayer(player), location, gravityGunFlag);
    }

    private static StateFlag registerStateFlag(FlagRegistry registry, String name, boolean defaultValue)
            throws FlagConflictException {
        StateFlag flag = new StateFlag(name, defaultValue);
        try {
            registry.register(flag);
            return flag;
        } catch (FlagConflictException conflict) {
            Flag<?> existing = registry.get(name);
            if (existing instanceof StateFlag) {
                return (StateFlag) existing;
            }
            throw conflict;
        }
    }

    private static boolean testFlag(LocalPlayer localPlayer, Location location, StateFlag flag) {
        if (flag == null || location == null || location.getWorld() == null) {
            return true;
        }

        if (regionContainer == null) {
            return true;
        }

        RegionQuery query = regionContainer.createQuery();
        com.sk89q.worldedit.util.Location adapted = BukkitAdapter.adapt(location);
        ApplicableRegionSet set = query.getApplicableRegions(adapted);
        return set.testState(localPlayer, flag);
    }
}
