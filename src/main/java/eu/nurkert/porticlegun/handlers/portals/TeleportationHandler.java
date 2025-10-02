package eu.nurkert.porticlegun.handlers.portals;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.handlers.gravity.GravityGun;
import eu.nurkert.porticlegun.portals.Portal;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportationHandler implements Listener {

    private static final long TELEPORT_COOLDOWN_MS = 1000;

    private final Map<UUID, Long> teleportCooldown;
    private final Map<UUID, Portal> portalReentryLock;
    public TeleportationHandler() {
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

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        ArrayList<Portal> portals = ActivePortalsHandler.getRelevantPortals(player);
        for (Portal portal : portals) {
            boolean inPortal = isEntityInPortal(portal, event.getTo());
            if (inPortal) {
                if (isPortalLocked(player, portal)) {
                    continue;
                }
                if (teleportEntityThroughPortal(player, portal)) {
                    break;
                }
            } else {
                clearPortalLockIfMatches(player, portal);
            }
        }

    }

    private void tickEntityTeleports() {
        cleanupCooldowns();

        for (Portal portal : ActivePortalsHandler.getAllPortal()) {
            Portal linked = portal.getLinkedPortal();
            if (linked == null) {
                continue;
            }

            Location portalLocation = portal.getLocation();
            if (portalLocation.getWorld() == null) {
                continue;
            }

            Collection<Entity> nearbyEntities = portalLocation.getWorld().getNearbyEntities(portalLocation, 1.0, 1.5, 1.0);
            for (Entity entity : nearbyEntities) {
                if (entity instanceof Player) {
                    continue;
                }

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
        portalReentryLock.entrySet().removeIf(entry -> {
            Portal lockedPortal = entry.getValue();
            if (lockedPortal == null || lockedPortal.getLocation() == null || lockedPortal.getLocation().getWorld() == null) {
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

        Portal linkedPortal = portal.getLinkedPortal();
        if (linkedPortal == null || linkedPortal.getLocation().getWorld() == null) {
            return false;
        }

        Vector preTeleportVelocity = entity.getVelocity().clone();
        Vector preTeleportLook = null;
        if (entity instanceof LivingEntity) {
            preTeleportLook = ((LivingEntity) entity).getLocation().getDirection().clone();
        }

        PortalBasis sourceBasis = createPortalBasis(portal.getDirection());
        PortalBasis destinationBasis = createPortalBasis(linkedPortal.getDirection());

        Vector transformedVelocity = transformVector(preTeleportVelocity, sourceBasis, destinationBasis);

        Location destination = linkedPortal.getLocation().clone().add(0.5, linkedPortal.getDirection().getY() == -1.0 ? -1 : 0, 0.5);

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

        setPortalLock(entity, linkedPortal);
        setCooldown(entity);
        return true;
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
