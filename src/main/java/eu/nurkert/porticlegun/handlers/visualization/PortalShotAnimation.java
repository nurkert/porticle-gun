package eu.nurkert.porticlegun.handlers.visualization;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.config.ConfigManager;
import eu.nurkert.porticlegun.portals.PotentialPortal;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.function.BooleanSupplier;

public final class PortalShotAnimation {

    private static final double STEP_DISTANCE = 0.6;
    private static final double SWIRL_RADIUS = 0.35;
    private static final double IMPACT_RADIUS = 0.55;

    private PortalShotAnimation() {
    }

    public static void play(Player player, PotentialPortal potentialTarget, PortalColor color,
                            BooleanSupplier onImpact) {
        if (player == null) {
            return;
        }

        Location start = player.getEyeLocation();
        World world = start.getWorld();
        if (world == null) {
            if (onImpact != null) {
                onImpact.getAsBoolean();
            }
            return;
        }

        Vector forward = start.getDirection().clone();
        if (forward.lengthSquared() == 0.0) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();

        Location impactPoint;
        double travelDistance;
        if (potentialTarget != null) {
            impactPoint = PortalGeometry.computePortalCenter(potentialTarget);
            travelDistance = start.distance(impactPoint);
        } else {
            double maxDistance = ConfigManager.getPortalMaxTargetDistance();
            impactPoint = start.clone().add(forward.clone().multiply(maxDistance));
            travelDistance = maxDistance;
        }

        if (travelDistance < 0.1) {
            travelDistance = 0.1;
        }

        Vector right = computeRightVector(forward);
        Vector up = forward.clone().crossProduct(right).normalize();
        Vector step = forward.clone().multiply(STEP_DISTANCE);
        int totalSteps = Math.max(1, (int) Math.ceil(travelDistance / STEP_DISTANCE));
        Particle.DustOptions dustOptions = color.getDustOptions();

        new BukkitRunnable() {
            int currentStep = 0;
            final Location current = start.clone();

            @Override
            public void run() {
                if (!player.isOnline() || player.getWorld() != world) {
                    cancel();
                    return;
                }

                double progress = currentStep / (double) totalSteps;
                spawnTrail(world, current, right, up, dustOptions, progress);

                current.add(step);
                currentStep++;
                if (currentStep >= totalSteps) {
                    boolean success = onImpact != null && onImpact.getAsBoolean();
                    spawnImpact(world, impactPoint, right, up, dustOptions, success);
                    cancel();
                }
            }
        }.runTaskTimer(PorticleGun.getInstance(), 0L, 1L);
    }

    private static void spawnTrail(World world, Location point, Vector right, Vector up,
                                   Particle.DustOptions dustOptions, double progress) {
        double swirlTurns = 5.0;
        double baseAngle = swirlTurns * Math.PI * 2 * progress;

        for (int arm = 0; arm < 2; arm++) {
            double angle = baseAngle + arm * Math.PI;
            Vector offset = right.clone().multiply(Math.cos(angle) * SWIRL_RADIUS)
                    .add(up.clone().multiply(Math.sin(angle) * SWIRL_RADIUS));
            Location swirl = point.clone().add(offset);
            world.spawnParticle(Particle.DUST, swirl, 1, 0.0, 0.0, 0.0, 0.0, dustOptions);
        }

        world.spawnParticle(Particle.CRIT, point.getX(), point.getY(), point.getZ(), 2, 0.0, 0.0, 0.0, 0.0);
    }

    private static void spawnImpact(World world, Location impactPoint, Vector right, Vector up,
                                    Particle.DustOptions dustOptions, boolean success) {
        if (impactPoint == null) {
            return;
        }

        double sparks = success ? 24 : 12;
        for (int i = 0; i < sparks; i++) {
            double angle = (Math.PI * 2 / sparks) * i;
            Vector offset = right.clone().multiply(Math.cos(angle) * IMPACT_RADIUS)
                    .add(up.clone().multiply(Math.sin(angle) * IMPACT_RADIUS));
            Location particleLocation = impactPoint.clone().add(offset);
            if (success) {
                world.spawnParticle(Particle.END_ROD, particleLocation, 1, 0.0, 0.0, 0.0, 0.0);
            } else {
                world.spawnParticle(Particle.CLOUD, particleLocation, 1, 0.0, 0.0, 0.0, 0.01);
            }
        }

        if (success) {
            world.spawnParticle(Particle.PORTAL, impactPoint, 30, 0.25, 0.25, 0.25, 0.05);
        } else {
            world.spawnParticle(Particle.CRIT, impactPoint, 16, 0.25, 0.25, 0.25, 0.15);
        }
    }

    private static Vector computeRightVector(Vector forward) {
        Vector reference = Math.abs(forward.getY()) < 0.95 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        Vector right = reference.clone().crossProduct(forward);
        if (right.lengthSquared() == 0.0) {
            right = new Vector(1, 0, 0);
        }
        right.normalize();
        return right;
    }
}
