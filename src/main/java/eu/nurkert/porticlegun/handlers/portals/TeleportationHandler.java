package eu.nurkert.porticlegun.handlers.portals;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.handlers.gravity.GravityGun;
import eu.nurkert.porticlegun.portals.Portal;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class TeleportationHandler implements Listener {

    private final Map<UUID, BlockKey> lastPortalBlocks = new HashMap<>();
    private BukkitTask entityMonitorTask;

    public void startEntityMonitor() {
        if (entityMonitorTask != null) {
            return;
        }

        entityMonitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                monitorEntities();
            }
        }.runTaskTimer(PorticleGun.getInstance(), 1L, 1L);
    }

    @EventHandler
    public void on(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        if (event.getFrom().getBlockX() == to.getBlockX()
                && event.getFrom().getBlockY() == to.getBlockY()
                && event.getFrom().getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        World playerWorld = to.getWorld();
        if (playerWorld == null) {
            return;
        }

        Collection<Portal> portals = ActivePortalsHandler.getAllPortals();
        if (portals.isEmpty()) {
            return;
        }

        for (Portal portal : portals) {
            Location portalLocation = portal.getLocation();
            if (portalLocation == null || portalLocation.getWorld() != playerWorld) {
                continue;
            }

            if (!isEntityInsidePortal(to, portal)) {
                continue;
            }

            if (!shouldTeleport(player, portal)) {
                continue;
            }

            if (teleportEntity(player, portal)) {
                break;
            }
        }
    }

    private void monitorEntities() {
        cleanupPortalVisits();

        Collection<Portal> portals = ActivePortalsHandler.getAllPortals();
        if (portals.isEmpty()) {
            return;
        }

        for (Portal portal : portals) {
            Portal linked = portal.getLinkedPortal();
            if (linked == null) {
                continue;
            }

            Location portalLocation = portal.getLocation();
            if (portalLocation == null) {
                continue;
            }

            World world = portalLocation.getWorld();
            if (world == null) {
                continue;
            }

            Collection<Entity> nearby = world.getNearbyEntities(portalLocation.clone().add(0.5, 0.5, 0.5), 1.0, 1.0, 1.0);
            if (nearby.isEmpty()) {
                continue;
            }

            for (Entity entity : nearby) {
                if (entity instanceof Player || entity.isDead() || !entity.isValid()) {
                    continue;
                }

                if (!(entity instanceof LivingEntity) && !(entity instanceof FallingBlock)) {
                    continue;
                }

                Location entityLocation = entity.getLocation();
                if (entityLocation == null) {
                    continue;
                }

                if (!isEntityInsidePortal(entityLocation, portal)) {
                    continue;
                }

                if (!shouldTeleport(entity, portal)) {
                    continue;
                }

                teleportEntity(entity, portal);
            }
        }
    }

    private boolean isEntityInsidePortal(Location location, Portal portal) {
        if (location == null) {
            return false;
        }

        if (portal.isInPortal(location)) {
            return true;
        }

        if (portal.getDirection().getY() == -1.0) {
            Location above = location.clone().add(0, 1, 0);
            return portal.isInPortal(above);
        }

        return false;
    }

    private boolean shouldTeleport(Entity entity, Portal portal) {
        Location portalLocation = portal.getLocation();
        if (portalLocation == null || portalLocation.getWorld() == null) {
            return false;
        }

        BlockKey portalBlock = BlockKey.fromLocation(portalLocation);
        if (portalBlock == null) {
            return false;
        }

        BlockKey previous = lastPortalBlocks.get(entity.getUniqueId());
        if (portalBlock.equals(previous)) {
            return false;
        }

        return true;
    }

    private boolean teleportEntity(Entity entity, Portal source) {
        Portal destinationPortal = source.getLinkedPortal();
        if (destinationPortal == null) {
            return false;
        }

        Location destination = buildDestination(destinationPortal);
        if (destination == null) {
            return false;
        }

        double speed = entity.getVelocity().length();

        String spawnerType = null;
        if (entity instanceof FallingBlock && entity.hasMetadata("spawner_type") && !entity.getMetadata("spawner_type").isEmpty()) {
            spawnerType = entity.getMetadata("spawner_type").get(0).asString();
        }

        boolean teleported = entity.teleport(destination);
        if (!teleported) {
            return false;
        }

        GravityGun gravityGun = GravityGun.getInstance();
        if (gravityGun != null) {
            gravityGun.releaseEntity(entity);
        }

        if (spawnerType != null) {
            entity.setMetadata("spawner_type", new FixedMetadataValue(PorticleGun.getInstance(), spawnerType));
        }

        Vector direction = destination.getDirection().clone();
        if (direction.lengthSquared() > 0) {
            direction = direction.normalize().multiply(speed);
        } else {
            direction.zero();
        }

        entity.setVelocity(direction);
        entity.setFallDistance(0f);

        Location destinationLocation = destinationPortal.getLocation();
        if (destinationLocation != null) {
            BlockKey destinationBlock = BlockKey.fromLocation(destinationLocation);
            if (destinationBlock != null) {
                lastPortalBlocks.put(entity.getUniqueId(), destinationBlock);
            } else {
                lastPortalBlocks.remove(entity.getUniqueId());
            }
        }

        return true;
    }

    private Location buildDestination(Portal portal) {
        Location base = portal.getLocation();
        if (base == null) {
            return null;
        }

        Location destination = base.clone().add(0.5, portal.getDirection().getY() == -1.0 ? -1 : 0, 0.5);
        destination.setDirection(portal.getDirection());
        return destination;
    }

    private void cleanupPortalVisits() {
        Collection<Portal> portals = ActivePortalsHandler.getAllPortals();
        Iterator<Map.Entry<UUID, BlockKey>> iterator = lastPortalBlocks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BlockKey> entry = iterator.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || entity.isDead() || !entity.isValid()) {
                iterator.remove();
                continue;
            }

            Portal portal = findPortalForBlock(portals, entry.getValue());
            if (portal == null) {
                iterator.remove();
                continue;
            }

            if (!isEntityInsidePortal(entity.getLocation(), portal)) {
                iterator.remove();
            }
        }
    }

    private Portal findPortalForBlock(Collection<Portal> portals, BlockKey blockKey) {
        if (blockKey == null || portals.isEmpty()) {
            return null;
        }
        for (Portal portal : portals) {
            if (portal == null) {
                continue;
            }
            Location location = portal.getLocation();
            if (location == null) {
                continue;
            }
            BlockKey portalBlock = BlockKey.fromLocation(location);
            if (portalBlock != null && portalBlock.equals(blockKey)) {
                return portal;
            }
        }
        return null;
    }

    private static final class BlockKey {
        private final String worldName;
        private final int x;
        private final int y;
        private final int z;

        private BlockKey(String worldName, int x, int y, int z) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static BlockKey fromLocation(Location location) {
            if (location == null || location.getWorld() == null) {
                return null;
            }
            return new BlockKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            BlockKey blockKey = (BlockKey) object;
            return x == blockKey.x && y == blockKey.y && z == blockKey.z && worldName.equals(blockKey.worldName);
        }

        @Override
        public int hashCode() {
            int result = worldName.hashCode();
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }
}
