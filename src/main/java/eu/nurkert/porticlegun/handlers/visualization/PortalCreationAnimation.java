package eu.nurkert.porticlegun.handlers.visualization;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.handlers.portals.ActivePortalsHandler;
import eu.nurkert.porticlegun.handlers.visualization.GunColorHandler;
import eu.nurkert.porticlegun.handlers.visualization.PortalGeometry;
import eu.nurkert.porticlegun.portals.Portal;
import eu.nurkert.porticlegun.handlers.visualization.concrete.PortalVisualizationType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public final class PortalCreationAnimation {

    public static final int ANIMATION_DURATION_TICKS = 30;
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    private PortalCreationAnimation() {
    }

    public static void play(Portal portal) {
        if (portal == null) {
            return;
        }

        final Location center = PortalGeometry.computePortalCenter(portal);
        if (center == null) {
            return;
        }
        final World world = center.getWorld();
        if (world == null) {
            return;
        }

        portal.delayVisualizationByTicks(ANIMATION_DURATION_TICKS);

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
        int pointsPerTick = 24;

        PortalVisualizationType visualizationType = portal.getVisualizationType();
        if (visualizationType == null) {
            visualizationType = PortalVisualizationType.RECTANGULAR;
        }

        final Vector finalRight = right.clone();
        final Vector finalUp = up.clone();
        final Particle.DustOptions finalDustOptions = dustOptions;
        final double finalMaxRight = maxRight;
        final double finalMaxUp = maxUp;
        final int finalPointsPerTick = pointsPerTick;
        final PortalVisualizationType finalVisualizationType = visualizationType;

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
                ThreadLocalRandom random = ThreadLocalRandom.current();

                switch (finalVisualizationType) {
                    case ELLIPTIC -> spawnEllipticPattern(world, center, finalRight, finalUp, finalDustOptions,
                            finalMaxRight, finalMaxUp, finalPointsPerTick, progress, easing, random);
                    case RECTANGULAR -> spawnRectangularPattern(world, center, finalRight, finalUp, finalDustOptions,
                            finalMaxRight, finalMaxUp, finalPointsPerTick, progress, easing, random);
                }

                spawnAmbientSparkles(world, center, finalRight, finalUp, finalMaxRight, finalMaxUp, progress, random);
                tick++;
            }
        }.runTaskTimer(PorticleGun.getInstance(), 0L, 1L);
    }

    private static void spawnEllipticPattern(World world, Location center, Vector right, Vector up,
                                             Particle.DustOptions dustOptions, double maxRight, double maxUp,
                                             int pointsPerTick, double progress, double easing,
                                             ThreadLocalRandom random) {
        double spiralRotation = progress * Math.PI * 1.1;
        double wave = Math.sin(progress * Math.PI) * 0.6;

        for (int i = 0; i < pointsPerTick; i++) {
            double normalizedIndex = (i + 1) / (double) pointsPerTick;
            double radiusFactor = Math.pow(normalizedIndex, 0.75) * easing;
            double angle = GOLDEN_ANGLE * i + spiralRotation;

            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            double radialPulse = Math.sin(angle * 0.75 + progress * Math.PI * 2) * 0.05 * wave;
            double x = cos * (radiusFactor + radialPulse) * maxRight;
            double y = sin * (radiusFactor - radialPulse) * maxUp;

            Vector offset = right.clone().multiply(x).add(up.clone().multiply(y));
            Location particleLocation = center.clone().add(offset);

            world.spawnParticle(Particle.DUST, particleLocation.getX(), particleLocation.getY(), particleLocation.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0, dustOptions);

            if (i % 6 == 0) {
                spawnPortalParticle(world, particleLocation, maxRight, maxUp, random);
            }
        }
    }

    private static void spawnRectangularPattern(World world, Location center, Vector right, Vector up,
                                                Particle.DustOptions dustOptions, double maxRight, double maxUp,
                                                int pointsPerTick, double progress, double easing,
                                                ThreadLocalRandom random) {
        double halfWidth = maxRight * (0.35 + 0.65 * easing);
        double halfHeight = maxUp * (0.35 + 0.65 * easing);
        double width = halfWidth * 2;
        double height = halfHeight * 2;
        double perimeter = 2 * (width + height);

        for (int i = 0; i < pointsPerTick; i++) {
            double slide = (i / (double) pointsPerTick + progress * 0.8) % 1.0;
            double distance = slide * perimeter;
            Vector edgePoint = pointOnRectangle(distance, halfWidth, halfHeight);

            double jitterRight = (random.nextDouble() - 0.5) * 0.08 * (1.0 - easing);
            double jitterUp = (random.nextDouble() - 0.5) * 0.08 * (1.0 - easing);

            Vector offset = right.clone().multiply(edgePoint.getX() + jitterRight)
                    .add(up.clone().multiply(edgePoint.getY() + jitterUp));
            Location particleLocation = center.clone().add(offset);

            world.spawnParticle(Particle.DUST, particleLocation.getX(), particleLocation.getY(), particleLocation.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0, dustOptions);

            if (i % 4 == 0) {
                spawnPortalParticle(world, particleLocation, maxRight, maxUp, random);
            }
        }
    }

    private static Vector pointOnRectangle(double distance, double halfWidth, double halfHeight) {
        double width = halfWidth * 2;
        double height = halfHeight * 2;
        double perimeter = 2 * (width + height);
        double d = distance % perimeter;

        if (d < width) {
            return new Vector(-halfWidth + d, halfHeight, 0);
        }
        d -= width;
        if (d < height) {
            return new Vector(halfWidth, halfHeight - d, 0);
        }
        d -= height;
        if (d < width) {
            return new Vector(halfWidth - d, -halfHeight, 0);
        }
        d -= width;
        return new Vector(-halfWidth, -halfHeight + d, 0);
    }

    private static void spawnAmbientSparkles(World world, Location center, Vector right, Vector up,
                                             double maxRight, double maxUp, double progress, ThreadLocalRandom random) {
        int sparkleCount = 3;
        double innerScale = 0.25 + 0.55 * progress;
        for (int i = 0; i < sparkleCount; i++) {
            double randRight = (random.nextDouble() - 0.5) * 2 * maxRight * innerScale;
            double randUp = (random.nextDouble() - 0.5) * 2 * maxUp * innerScale;

            Vector offset = right.clone().multiply(randRight).add(up.clone().multiply(randUp));
            Location sparkleLocation = center.clone().add(offset);
            spawnPortalParticle(world, sparkleLocation, maxRight, maxUp, random);
        }
    }

    private static void spawnPortalParticle(World world, Location location, double maxRight, double maxUp,
                                            ThreadLocalRandom random) {
        double spreadRight = maxRight * 0.15;
        double spreadUp = maxUp * 0.15;
        world.spawnParticle(Particle.PORTAL, location.getX(), location.getY(), location.getZ(),
                1,
                (random.nextDouble() - 0.5) * spreadRight,
                (random.nextDouble() - 0.5) * spreadUp,
                (random.nextDouble() - 0.5) * spreadRight,
                0.1);
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