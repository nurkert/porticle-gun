package eu.nurkert.porticlegun.handlers.visualization;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.handlers.portals.ActivePortalsHandler;
import eu.nurkert.porticlegun.portals.Portal;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public final class PortalCreationAnimation {

    private static final int ANIMATION_DURATION_TICKS = 30;
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    private PortalCreationAnimation() {
    }

    public static void play(Portal portal) {
        if (portal == null) {
            return;
        }

        final Location center = computePortalCenter(portal);
        if (center == null) {
            return;
        }
        final World world = center.getWorld();
        if (world == null) {
            return;
        }

        Vector normal = portal.getDirection().clone().normalize();
        Vector reference = Math.abs(normal.getY()) < 0.99 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        Vector right = reference.clone().crossProduct(normal);
        if (right.lengthSquared() == 0) {
            reference = new Vector(1, 0, 0);
            right = reference.clone().crossProduct(normal);
        }
        right.normalize();
        Vector up = normal.clone().crossProduct(right).normalize();

        Color color = GunColorHandler.getColors(portal.getGunID()).get(portal.getType()).getBukkitColor();
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 0.9F);

        double maxRight = portal.getDirection().getY() == 0.0 ? 0.35 : 0.45;
        double maxUp = portal.getDirection().getY() == 0.0 ? 0.75 : 0.45;
        int pointsPerTick = 20;

        final Vector finalRight = right.clone();
        final Vector finalUp = up.clone();
        final Particle.DustOptions finalDustOptions = dustOptions;
        final double finalMaxRight = maxRight;
        final double finalMaxUp = maxUp;
        final int finalPointsPerTick = pointsPerTick;

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= ANIMATION_DURATION_TICKS || !isPortalStillActive(portal)) {
                    cancel();
                    return;
                }

                double progress = (tick + 1) / (double) ANIMATION_DURATION_TICKS;
                double easing = 1 - Math.pow(1 - progress, 2.5);
                double spiralRotation = tick * 0.15;
                double wave = Math.sin(progress * Math.PI) * 0.5;

                for (int i = 0; i < finalPointsPerTick; i++) {
                    double normalizedIndex = (i + 1) / (double) finalPointsPerTick;
                    double radiusFactor = Math.sqrt(normalizedIndex) * easing;
                    double angle = GOLDEN_ANGLE * i + spiralRotation;
                    double sin = Math.sin(angle);
                    double cos = Math.cos(angle);

                    double x = cos * radiusFactor * finalMaxRight;
                    double y = sin * radiusFactor * finalMaxUp;
                    double pulse = Math.sin(angle + progress * Math.PI * 1.5) * 0.02 * wave;

                    Vector offset = finalRight.clone().multiply(x + pulse).add(finalUp.clone().multiply(y - pulse));
                    Location particleLocation = center.clone().add(offset);
                    world.spawnParticle(
                            Particle.DUST,
                            particleLocation.getX(), particleLocation.getY(), particleLocation.getZ(),
                            1, 0.0, 0.0, 0.0, 0.0, finalDustOptions
                    );
                }

                tick++;
            }
        }.runTaskTimer(PorticleGun.getInstance(), 0L, 1L);
    }

    private static Location computePortalCenter(Portal portal) {
        Location base = portal.getLocation().clone();
        Vector direction = portal.getDirection();
        if (direction.getY() == 0.0) {
            return base.add(0.5 - 0.4 * direction.getX(), 1.0, 0.5 - 0.4 * direction.getZ());
        } else if (direction.getY() < 0.0) {
            return base.add(0.5, 0.9, 0.5);
        } else if (direction.getY() > 0.0) {
            return base.add(0.5, 0.1, 0.5);
        }
        return base;
    }

    private static boolean isPortalStillActive(Portal portal) {
        if (portal.getType() == Portal.PortalType.PRIMARY) {
            return ActivePortalsHandler.getPrimaryPortal(portal.getGunID()) == portal;
        }
        if (portal.getType() == Portal.PortalType.SECONDARY) {
            return ActivePortalsHandler.getSecondaryPortal(portal.getGunID()) == portal;
        }
        return false;
    }
}