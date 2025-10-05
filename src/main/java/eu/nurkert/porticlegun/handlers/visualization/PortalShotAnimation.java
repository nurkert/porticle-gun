package eu.nurkert.porticlegun.handlers.visualization;

import eu.nurkert.porticlegun.PorticleGun;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class PortalShotAnimation {

    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));
    private static final double SHOT_SPEED_BLOCKS_PER_TICK = 2.75;
    private static final int CORE_SPARK_COUNT = 5;

    private PortalShotAnimation() {
    }

    public static void play(Player shooter, Location target, Color color, boolean success,
                            Runnable onSuccess, Runnable onFailure) {
        if (shooter == null || target == null) {
            finish(success, onSuccess, onFailure);
            return;
        }

        Location start = computeMuzzleLocation(shooter);
        if (start == null) {
            finish(success, onSuccess, onFailure);
            return;
        }

        final World world = start.getWorld();
        final World targetWorld = target.getWorld();
        if (world == null || targetWorld == null || !Objects.equals(world, targetWorld)) {
            finish(success, onSuccess, onFailure);
            return;
        }

        Vector delta = target.clone().subtract(start).toVector();
        double distance = delta.length();
        if (distance < 1E-3) {
            spawnImpact(world, target, color, success);
            finish(success, onSuccess, onFailure);
            return;
        }

        final Vector direction = delta.clone().normalize();
        Vector computedRight = direction.clone().crossProduct(new Vector(0, 1, 0));
        if (computedRight.lengthSquared() < 1E-3) {
            computedRight = direction.clone().crossProduct(new Vector(1, 0, 0));
        }
        computedRight.normalize();
        final Vector right = computedRight;
        final Vector up = direction.clone().crossProduct(right).normalize();

        final int totalTicks = Math.max(1, (int) Math.ceil(distance / SHOT_SPEED_BLOCKS_PER_TICK));
        final Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.2F);
        final Location current = start.clone();
        final Vector step = direction.clone().multiply(distance / totalTicks);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!shooter.isOnline() || shooter.isDead()) {
                    cancel();
                    finish(false, onSuccess, onFailure);
                    return;
                }

                double progress = tick / (double) totalTicks;
                spawnBeamParticles(world, current, direction, right, up, dustOptions, progress, success);
                current.add(step);

                tick++;
                if (tick >= totalTicks) {
                    spawnImpact(world, target, color, success);
                    cancel();
                    finish(success, onSuccess, onFailure);
                }
            }
        }.runTaskTimer(PorticleGun.getInstance(), 0L, 1L);
    }

    private static void spawnBeamParticles(World world, Location current, Vector direction, Vector right, Vector up,
                                           Particle.DustOptions dustOptions, double progress,
                                           boolean success) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        world.spawnParticle(Particle.END_ROD, current.getX(), current.getY(), current.getZ(),
                0, direction.getX() * 0.12, direction.getY() * 0.12, direction.getZ() * 0.12, 0.0);

        double coreRadius = 0.12 + 0.04 * Math.sin(progress * Math.PI * 0.5);
        for (int i = 0; i < CORE_SPARK_COUNT; i++) {
            double angle = GOLDEN_ANGLE * (progress * 20 + i);
            double radius = coreRadius * (0.6 + 0.4 * random.nextDouble());
            Vector offset = right.clone().multiply(Math.cos(angle) * radius)
                    .add(up.clone().multiply(Math.sin(angle) * radius));
            Location swirl = current.clone().add(offset);
            world.spawnParticle(Particle.DUST, swirl.getX(), swirl.getY(), swirl.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0, dustOptions);

            if (i % 2 == 0) {
                world.spawnParticle(success ? Particle.ENCHANTED_HIT : Particle.WITCH,
                        swirl.getX(), swirl.getY(), swirl.getZ(), 0, 0.0, 0.0, 0.0, 0.0);
            }
        }

        if (random.nextDouble() < 0.4) {
            double jitterRight = (random.nextDouble() - 0.5) * 0.3 * (1.0 - progress);
            double jitterUp = (random.nextDouble() - 0.5) * 0.3 * (1.0 - progress);
            Vector offset = right.clone().multiply(jitterRight).add(up.clone().multiply(jitterUp));
            Location ambient = current.clone().add(offset);
            world.spawnParticle(Particle.PORTAL, ambient.getX(), ambient.getY(), ambient.getZ(), 1,
                    direction.getX() * 0.05, direction.getY() * 0.05, direction.getZ() * 0.05, 0.05);
        }
    }

    private static void spawnImpact(World world, Location location, Color color, boolean success) {
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, success ? 1.3F : 0.9F);
        for (int i = 0; i < (success ? 22 : 12); i++) {
            Vector spread = randomSphereVector().multiply(success ? 0.35 : 0.25);
            world.spawnParticle(Particle.DUST, location.getX(), location.getY(), location.getZ(), 0,
                    spread.getX(), spread.getY(), spread.getZ(), 0.0, dustOptions);
            world.spawnParticle(success ? Particle.PORTAL : Particle.SMOKE,
                    location.getX(), location.getY(), location.getZ(), 0,
                    spread.getX() * 0.8, spread.getY() * 0.8, spread.getZ() * 0.8, 0.05);
        }
        world.spawnParticle(Particle.END_ROD, location.getX(), location.getY(), location.getZ(), success ? 18 : 8,
                0.15, 0.15, 0.15, 0.01);
    }

    private static Vector randomSphereVector() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double theta = random.nextDouble(0, Math.PI * 2);
        double phi = Math.acos(2 * random.nextDouble() - 1);
        double sinPhi = Math.sin(phi);
        return new Vector(Math.cos(theta) * sinPhi, Math.cos(phi), Math.sin(theta) * sinPhi);
    }

    private static Location computeMuzzleLocation(Player shooter) {
        Location eye = shooter.getEyeLocation().clone();
        Vector direction = eye.getDirection().normalize();
        Vector right = direction.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1E-3) {
            right = direction.clone().crossProduct(new Vector(1, 0, 0));
        }
        right.normalize();
        Vector up = right.clone().crossProduct(direction).normalize();

        eye.add(direction.clone().multiply(0.4));
        eye.add(right.multiply(0.35));
        eye.subtract(up.multiply(0.15));
        return eye;
    }

    private static void finish(boolean success, Runnable onSuccess, Runnable onFailure) {
        if (success) {
            if (onSuccess != null) {
                onSuccess.run();
            }
        } else {
            if (onFailure != null) {
                onFailure.run();
            }
        }
    }
}
