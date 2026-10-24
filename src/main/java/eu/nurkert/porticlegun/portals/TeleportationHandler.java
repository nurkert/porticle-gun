package eu.nurkert.porticlegun.portals;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.audio.AudioHandler;
import eu.nurkert.porticlegun.gravity.GravityGun;
import eu.nurkert.porticlegun.util.WorldGuardIntegration;
import eu.nurkert.porticlegun.visualization.GunColors;
import eu.nurkert.porticlegun.visualization.VisualizationHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Teleports players and other entities between linked portals and keeps their momentum and view direction.
 */
public class TeleportationHandler implements Listener {

    // short enough for portals stacked a few blocks apart, where a falling player loops through them forever
    private static final long TELEPORT_COOLDOWN_MS = 150;
    // fall damage is cancelled this long after a teleport, and whenever someone lands on a portal
    private static final long FALL_PROTECTION_MS = 600;
    // players move faster than this only when something is wrong; keeps a lag spike from flinging them away
    private static final double MAX_SPEED = 4.0;
    // a blocked portal pushes back at most this often per entity, so standing in it is a soft bounce, not a launch
    private static final long REPEL_COOLDOWN_MS = 350;

    private final Map<UUID, Long> teleportCooldown;
    private final Map<UUID, Portal> portalReentryLock;
    // block -> the linked portals in it, so a player's move is checked with one lookup
    private final Map<CellKey, List<Portal>> cells = new HashMap<>();
    // a player's movement during the last tick: the server does not know a player's real velocity
    private final Map<UUID, Vector> playerMotion = new HashMap<>();
    private final Set<UUID> heldMotion = new HashSet<>();
    private final Map<UUID, Long> lastRepel = new HashMap<>();

    private final ActivePortalsHandler activePortals;

    public TeleportationHandler(ActivePortalsHandler activePortals) {
        this.activePortals = activePortals;
        teleportCooldown = new HashMap<>();
        portalReentryLock = new HashMap<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                tickEntityTeleports();
            }
        }.runTaskTimer(PorticleGun.getInstance(), 1L, 1L);
    }

    @EventHandler
    public void on(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getWorld() == event.getTo().getWorld()) {
            Vector motion = event.getTo().toVector().subtract(event.getFrom().toVector());
            UUID id = event.getPlayer().getUniqueId();
            Vector previous = playerMotion.get(id);
            // landing on a floor portal cuts the last step short; for that one step the step before counts
            boolean stopped = previous != null
                    && !heldMotion.contains(id)
                    && motion.lengthSquared() < previous.lengthSquared() * 0.25;
            if (stopped) {
                heldMotion.add(id);
            } else {
                heldMotion.remove(id);
                playerMotion.put(id, motion);
            }
        }

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        // one hash lookup for the block the player moved into (and the one above, for ceiling portals) instead of a
        // loop over every portal
        Player player = event.getPlayer();
        Portal locked = portalReentryLock.get(player.getUniqueId());
        if (locked != null && !isEntityInPortal(locked, event.getTo())) {
            portalReentryLock.remove(player.getUniqueId());
        }
        Location to = event.getTo();
        for (int up = 0; up <= 1; up++) {
            List<Portal> here =
                    cells.get(new CellKey(to.getWorld().getUID(), to.getBlockX(), to.getBlockY() + up, to.getBlockZ()));
            if (here == null) {
                continue;
            }
            for (Portal portal : here) {
                if (isEntityInPortal(portal, to)
                        && !isPortalLocked(player, portal)
                        && teleportEntityThroughPortal(player, portal)) {
                    return;
                }
            }
        }
    }

    private record CellKey(UUID world, int x, int y, int z) {}

    /** Rebuilds the block -> portals grid; portals are few, so once per tick costs next to nothing. */
    private void index(List<Portal> portals) {
        cells.clear();
        for (Portal portal : portals) {
            Location location = portal.getLocation();
            if (portal.getLinkedPortal() == null || location == null || location.getWorld() == null) {
                continue;
            }
            cells.computeIfAbsent(
                            new CellKey(
                                    location.getWorld().getUID(),
                                    location.getBlockX(),
                                    location.getBlockY(),
                                    location.getBlockZ()),
                            key -> new ArrayList<>(1))
                    .add(portal);
        }
    }

    private void tickEntityTeleports() {
        cleanupCooldowns();
        List<Portal> portals = activePortals.getAllPortal();
        index(portals);

        for (Portal portal : portals) {
            Portal linked = portal.getLinkedPortal();
            if (linked == null) {
                continue;
            }

            Location portalLocation = portal.getLocation();
            if (portalLocation.getWorld() == null
                    || !portalLocation
                            .getWorld()
                            .isChunkLoaded(portalLocation.getBlockX() >> 4, portalLocation.getBlockZ() >> 4)) {
                continue;
            }

            Collection<Entity> nearbyEntities =
                    portalLocation.getWorld().getNearbyEntities(portalLocation, 1.0, 1.5, 1.0);
            // players too: one standing on a floor portal (after landing in the same moment) never moves into a new
            // block, so the move event alone would leave them there
            for (Entity entity : nearbyEntities) {
                if (!entity.isValid() || entity.isDead()) {
                    continue;
                }

                if (!entity.getWorld().equals(portalLocation.getWorld())) {
                    continue;
                }

                boolean inPortal = isEntityInPortal(portal, entity.getLocation());
                if (inPortal) {
                    if (isPortalLocked(entity, portal)) {
                        continue;
                    }
                    teleportEntityThroughPortal(entity, portal);
                } else {
                    clearPortalLockIfMatches(entity, portal);
                }
            }
        }
    }

    private void cleanupCooldowns() {
        long now = System.currentTimeMillis();
        teleportCooldown.entrySet().removeIf(entry -> now - entry.getValue() > TELEPORT_COOLDOWN_MS * 10);
        lastRepel.entrySet().removeIf(entry -> now - entry.getValue() > REPEL_COOLDOWN_MS * 10);
        portalReentryLock.entrySet().removeIf(entry -> {
            Portal lockedPortal = entry.getValue();
            if (lockedPortal == null
                    || lockedPortal.getLocation() == null
                    || lockedPortal.getLocation().getWorld() == null) {
                return true;
            }
            Entity entity = Bukkit.getEntity(entry.getKey());
            return entity == null || !entity.isValid();
        });
    }

    private boolean teleportEntityThroughPortal(Entity entity, Portal portal) {
        if (entity == null || portal == null) {
            return false;
        }

        if (isOnCooldown(entity)) {
            return false;
        }

        // one half of a double chest, bed or door would come apart from the other
        GravityGun carrier = GravityGun.getInstance();
        if (carrier != null && carrier.isMultiBlockPart(entity)) {
            return false;
        }

        Portal linkedPortal = portal.getLinkedPortal();
        if (linkedPortal == null || linkedPortal.getLocation().getWorld() == null) {
            return false;
        }

        if (activePortals.isBlocked(portal.getGunID())) {
            repel(entity, portal);
            return false;
        }

        if (PorticleGun.isWorldGuardEnabled()
                && !WorldGuardIntegration.canUsePortal(entity, portal.getLocation(), linkedPortal.getLocation())) {
            return false;
        }

        Vector preTeleportVelocity = velocity(entity);
        Vector preTeleportLook = null;
        if (entity instanceof LivingEntity) {
            preTeleportLook =
                    ((LivingEntity) entity).getLocation().getDirection().clone();
        }

        PortalBasis sourceBasis = createPortalBasis(portal.getDirection());
        PortalBasis destinationBasis = createPortalBasis(linkedPortal.getDirection());

        Vector transformedVelocity = transformVector(preTeleportVelocity, sourceBasis, destinationBasis);

        Location destination = linkedPortal
                .getLocation()
                .clone()
                .add(0.5, linkedPortal.getDirection().getY() == -1.0 ? -1 : 0, 0.5);

        if (preTeleportLook != null) {
            Vector transformedLook = transformVector(preTeleportLook, sourceBasis, destinationBasis);
            if (transformedLook.lengthSquared() > 0) {
                destination.setDirection(transformedLook);
            }
        }

        GravityGun gravityGun = GravityGun.getInstance();
        if (gravityGun != null) {
            gravityGun.releaseEntitySilently(entity);
        }

        boolean teleported = entity.teleport(destination);
        if (!teleported) {
            return false;
        }

        entity.setVelocity(transformedVelocity);
        entity.setFallDistance(0F);
        if (entity instanceof Player) {
            playerMotion.put(entity.getUniqueId(), transformedVelocity.clone());
            heldMotion.remove(entity.getUniqueId());
        }

        setPortalLock(entity, linkedPortal);
        setCooldown(entity);
        GunColors colors = PorticleGun.getInstance().getGunColors().getColors(portal.getGunID());
        VisualizationHandler.passThrough(
                portal,
                linkedPortal,
                colors.get(portal.getType()).getBukkitColor(),
                colors.get(linkedPortal.getType()).getBukkitColor());
        return true;
    }

    /**
     * Pushes an entity gently back out of a blocked portal: away from the wall (and a little up), up off a floor
     * portal, down from a ceiling portal.
     */
    private void repel(Entity entity, Portal portal) {
        long now = System.currentTimeMillis();
        Long last = lastRepel.get(entity.getUniqueId());
        if (last != null && now - last < REPEL_COOLDOWN_MS) {
            return;
        }
        lastRepel.put(entity.getUniqueId(), now);
        Vector direction = portal.getDirection().clone().normalize();
        Vector push = direction.getY() == 0.0
                ? direction.multiply(0.45).setY(0.2)
                : new Vector(0, direction.getY() > 0 ? 0.45 : -0.3, 0);
        entity.setVelocity(push);
        entity.setFallDistance(0F);
        VisualizationHandler.repelled(portal);
        if (entity instanceof Player player) {
            AudioHandler.playSound(player, AudioHandler.PortalSound.DENY);
        }
    }

    private Vector velocity(Entity entity) {
        Vector velocity = entity.getVelocity().clone();
        if (entity instanceof Player) {
            Vector motion = playerMotion.get(entity.getUniqueId());
            if (motion != null && motion.lengthSquared() > velocity.lengthSquared()) {
                velocity = motion.clone();
            }
        }
        if (velocity.lengthSquared() > MAX_SPEED * MAX_SPEED) {
            velocity.normalize().multiply(MAX_SPEED);
        }
        return velocity;
    }

    /**
     * No fall damage right after coming out of a portal, or when landing on a portal (falling through a floor portal
     * must never hurt, even when the landing is a moment faster than the teleport).
     */
    @EventHandler(ignoreCancelled = true)
    public void on(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        Entity entity = event.getEntity();
        Long lastTeleport = teleportCooldown.get(entity.getUniqueId());
        if (lastTeleport != null && System.currentTimeMillis() - lastTeleport < FALL_PROTECTION_MS) {
            event.setCancelled(true);
            return;
        }
        for (Portal portal : activePortals.getAllPortal()) {
            if (portal.getLinkedPortal() != null && isEntityInPortal(portal, entity.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void on(PlayerQuitEvent event) {
        playerMotion.remove(event.getPlayer().getUniqueId());
        heldMotion.remove(event.getPlayer().getUniqueId());
    }

    private boolean isOnCooldown(Entity entity) {
        Long lastTeleport = teleportCooldown.get(entity.getUniqueId());
        if (lastTeleport == null) {
            return false;
        }
        return System.currentTimeMillis() - lastTeleport < TELEPORT_COOLDOWN_MS;
    }

    private void setCooldown(Entity entity) {
        teleportCooldown.put(entity.getUniqueId(), System.currentTimeMillis());
    }

    private boolean isPortalLocked(Entity entity, Portal portal) {
        Portal lockedPortal = portalReentryLock.get(entity.getUniqueId());
        return lockedPortal != null && lockedPortal == portal;
    }

    private void clearPortalLockIfMatches(Entity entity, Portal portal) {
        Portal lockedPortal = portalReentryLock.get(entity.getUniqueId());
        if (lockedPortal != null && lockedPortal == portal) {
            portalReentryLock.remove(entity.getUniqueId());
        }
    }

    private void setPortalLock(Entity entity, Portal portal) {
        if (portal == null) {
            portalReentryLock.remove(entity.getUniqueId());
            return;
        }
        portalReentryLock.put(entity.getUniqueId(), portal);
    }

    private boolean isEntityInPortal(Portal portal, Location location) {
        if (location == null) {
            return false;
        }

        if (portal.isInPortal(location)) {
            return true;
        }

        if (portal.getDirection().getY() == -1.0) {
            Location upper = location.clone().add(0, 1, 0);
            return portal.isInPortal(upper);
        }

        return false;
    }

    static PortalBasis createPortalBasis(Vector direction) {
        Vector forward = direction.clone();
        if (forward.lengthSquared() == 0) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();

        Vector auxiliaryAxis = Math.abs(forward.getY()) > 0.999 ? new Vector(0, 0, 1) : new Vector(0, 1, 0);
        Vector right = auxiliaryAxis.clone().crossProduct(forward);
        if (right.lengthSquared() == 0) {
            auxiliaryAxis = new Vector(1, 0, 0);
            right = auxiliaryAxis.clone().crossProduct(forward);
        }
        right.normalize();

        Vector up = forward.clone().crossProduct(right).normalize();

        return new PortalBasis(forward, up, right);
    }

    static Vector transformVector(Vector vector, PortalBasis source, PortalBasis destination) {
        double forwardComponent = vector.dot(source.forward);
        double upComponent = vector.dot(source.up);
        double rightComponent = vector.dot(source.right);

        forwardComponent *= -1;
        rightComponent *= -1;

        Vector transformed = destination.forward.clone().multiply(forwardComponent);
        transformed.add(destination.up.clone().multiply(upComponent));
        transformed.add(destination.right.clone().multiply(rightComponent));
        return transformed;
    }

    static class PortalBasis {
        final Vector forward;
        final Vector up;
        final Vector right;

        PortalBasis(Vector forward, Vector up, Vector right) {
            this.forward = forward;
            this.up = up;
            this.right = right;
        }
    }
}
