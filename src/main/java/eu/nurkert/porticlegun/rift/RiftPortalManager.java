package eu.nurkert.porticlegun.rift;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.util.WorldGuardIntegration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Opens and runs the green rifts of the interdimensional gun: always a pair, one in front of the player and one at the
 * destination, both walkable in both directions until they close again.
 *
 * <p>A rift is drawn from particles only, so Java and Bedrock players see the same thing without a resource pack: a
 * wobbling oval rim with white speckles and three spiral arms turning towards the bright core. It opens and closes
 * by growing from and shrinking to its centre.
 */
public class RiftPortalManager implements Listener {

    // the colours of the swirl texture: pale rim with white speckles, light and dark green bands, no black
    private static final Particle.DustOptions RIM = new Particle.DustOptions(Color.fromRGB(0x9BD94C), 1.4f);
    private static final Particle.DustOptions SPECKLE = new Particle.DustOptions(Color.fromRGB(0xF4FFE6), 0.9f);
    private static final Particle.DustOptions LIGHT_GREEN = new Particle.DustOptions(Color.fromRGB(0xA6DE52), 1.0f);
    private static final Particle.DustOptions DARK_GREEN = new Particle.DustOptions(Color.fromRGB(0x2E9E2A), 1.0f);
    private static final Particle.DustOptions CORE = new Particle.DustOptions(Color.fromRGB(0xCDEE78), 1.6f);
    private static final int OPEN_TICKS = 10;
    private static final int RIM_POINTS = 26;
    private static final int ARMS = 3;
    private static final int ARM_POINTS = 5;
    // particles are only sent while a player is this close, the client would not show them farther away anyway
    private static final double VIEW_DISTANCE = 48;
    private static final double LEAVE_DISTANCE = 2.5;
    private static final int WALK_AWAY_TICKS = 40;

    private final PorticleGun plugin;
    private final RiftStorage storage;
    private final Map<UUID, RiftPortal[]> pairs = new HashMap<>();
    // portals still shrinking after they closed
    private final List<RiftPortal> closing = new ArrayList<>();
    // entity -> portal it just came out of; it cannot go back through until it stepped away
    private final Map<UUID, RiftPortal> exitLocks = new HashMap<>();
    // owner -> how far they may walk away, and the last tick they were that close to one of their portals
    private final Map<UUID, Double> closeDistances = new HashMap<>();
    private final Map<UUID, Long> lastNear = new HashMap<>();
    private final BukkitTask task;
    private long tick;

    public RiftPortalManager(PorticleGun plugin, RiftStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /**
     * Opens a rift pair for the player. An older pair of the same player closes first.
     *
     * @param lifetimeTicks how long both portals stay open at most
     * @param closeDistance the pair closes once the player is farther than this from both portals for a moment
     */
    public void open(
            Player owner,
            Location entranceFeet,
            Vector entranceNormal,
            Location exitFeet,
            Vector exitNormal,
            int lifetimeTicks,
            double closeDistance) {
        close(owner.getUniqueId());
        RiftPortal entrance = new RiftPortal(owner.getUniqueId(), entranceFeet, entranceNormal);
        RiftPortal exit = new RiftPortal(owner.getUniqueId(), exitFeet, exitNormal);
        entrance.partner = exit;
        exit.partner = entrance;
        for (RiftPortal portal : List.of(entrance, exit)) {
            portal.openedAt = tick;
            World world = portal.feet.getWorld();
            world.playSound(portal.centre(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 1.4f);
            world.playSound(portal.centre(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 0.8f, 1.2f);
        }
        pairs.put(owner.getUniqueId(), new RiftPortal[] {entrance, exit});
        UUID id = owner.getUniqueId();
        closeDistances.put(id, closeDistance);
        lastNear.put(id, tick + WALK_AWAY_TICKS); // a moment of grace after opening
        RiftPortal[] opened = pairs.get(id);
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            if (pairs.get(id) == opened) {
                                close(id);
                            }
                        },
                        lifetimeTicks);
    }

    public void close(UUID owner) {
        RiftPortal[] pair = pairs.remove(owner);
        closeDistances.remove(owner);
        lastNear.remove(owner);
        if (pair == null) {
            return;
        }
        for (RiftPortal portal : pair) {
            portal.feet.getWorld().playSound(portal.centre(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.6f);
            exitLocks.values().removeIf(locked -> locked == portal);
            portal.closedAt = tick;
            closing.add(portal);
        }
    }

    public void shutdown() {
        task.cancel();
        pairs.clear();
        closing.clear();
        exitLocks.clear();
    }

    private void tick() {
        tick++;
        closeAbandonedPairs();
        exitLocks.entrySet().removeIf(entry -> {
            Entity entity = Bukkit.getEntity(entry.getKey());
            return entity == null
                    || !entity.isValid()
                    || !entity.getWorld().equals(entry.getValue().feet.getWorld())
                    || entity.getLocation().distance(entry.getValue().feet) > LEAVE_DISTANCE;
        });
        boolean draw = tick % 2 == 0;
        for (RiftPortal[] pair : new ArrayList<>(pairs.values())) {
            for (RiftPortal portal : pair) {
                if (!loaded(portal)) {
                    continue;
                }
                if (draw) {
                    particles(portal, Math.min(1.0, (tick - portal.openedAt) / (double) OPEN_TICKS));
                }
                travel(portal);
            }
        }
        closing.removeIf(portal -> tick - portal.closedAt > OPEN_TICKS);
        if (draw) {
            for (RiftPortal portal : closing) {
                if (loaded(portal)) {
                    particles(portal, 1.0 - (tick - portal.closedAt) / (double) OPEN_TICKS);
                }
            }
        }
    }

    /**
     * Closes the pairs whose opener walked away from both portals (or left) for {@link #WALK_AWAY_TICKS}.
     */
    private void closeAbandonedPairs() {
        List<UUID> abandoned = new ArrayList<>();
        for (Map.Entry<UUID, RiftPortal[]> entry : pairs.entrySet()) {
            UUID owner = entry.getKey();
            Player player = Bukkit.getPlayer(owner);
            double distance = closeDistances.getOrDefault(owner, 12.0);
            boolean near = false;
            if (player != null) {
                for (RiftPortal portal : entry.getValue()) {
                    near |= player.getWorld().equals(portal.feet.getWorld())
                            && player.getLocation().distanceSquared(portal.feet) <= distance * distance;
                }
            }
            if (near) {
                lastNear.put(owner, Math.max(tick, lastNear.getOrDefault(owner, tick)));
            } else if (tick - lastNear.getOrDefault(owner, tick) > WALK_AWAY_TICKS) {
                abandoned.add(owner);
            }
        }
        abandoned.forEach(this::close);
    }

    /**
     * Draws one frame of the rift. size grows from 0 to 1 while it opens and shrinks back while it closes.
     */
    private void particles(RiftPortal portal, double size) {
        Location centre = portal.centre();
        World world = centre.getWorld();
        if (size <= 0 || !viewerNearby(world, centre)) {
            return;
        }
        double t = tick;
        double halfWidth = RiftPortal.HALF_WIDTH * size;
        double halfHeight = RiftPortal.HEIGHT / 2 * size;
        // the rim: an oval whose edge wobbles in two waves running in opposite directions
        for (int i = 0; i < RIM_POINTS; i++) {
            double angle = i * Math.PI * 2 / RIM_POINTS + t * 0.04;
            double wobble = rim(angle, t);
            boolean speckle = (i + tick / 4) % 6 == 0;
            spawn(
                    world,
                    portal,
                    centre,
                    Math.cos(angle) * halfWidth * wobble,
                    Math.sin(angle) * halfHeight * wobble,
                    speckle ? SPECKLE : RIM);
        }
        // three spiral arms turning towards the core, dark outside and light inside
        for (int arm = 0; arm < ARMS; arm++) {
            for (int j = 0; j < ARM_POINTS; j++) {
                double reach = 0.18 + 0.72 * (j + (tick / 2 % 2) * 0.5) / ARM_POINTS;
                double angle = arm * Math.PI * 2 / ARMS + 3.4 * reach - t * 0.16;
                double wobble = rim(angle, t) * reach;
                spawn(
                        world,
                        portal,
                        centre,
                        Math.cos(angle) * halfWidth * wobble,
                        Math.sin(angle) * halfHeight * wobble,
                        reach > 0.55 ? DARK_GREEN : LIGHT_GREEN);
            }
        }
        spawn(world, portal, centre, 0, 0, CORE);
    }

    private static boolean loaded(RiftPortal portal) {
        return portal.feet.getWorld().isChunkLoaded(portal.feet.getBlockX() >> 4, portal.feet.getBlockZ() >> 4);
    }

    private static double rim(double angle, double t) {
        return 0.94 + 0.05 * Math.sin(5 * angle + t * 0.22) + 0.03 * Math.sin(11 * angle - t * 0.35);
    }

    private static void spawn(
            World world, RiftPortal portal, Location centre, double side, double up, Particle.DustOptions colour) {
        world.spawnParticle(
                Particle.DUST,
                centre.getX() + portal.right.getX() * side,
                centre.getY() + up,
                centre.getZ() + portal.right.getZ() * side,
                1,
                0,
                0,
                0,
                0,
                colour);
    }

    private static boolean viewerNearby(World world, Location centre) {
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(centre) < VIEW_DISTANCE * VIEW_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    private void travel(RiftPortal portal) {
        Location centre = portal.centre();
        for (Entity entity : centre.getWorld().getNearbyEntities(centre, 1.6, 1.8, 1.6)) {
            if (!canTravel(entity) || exitLocks.get(entity.getUniqueId()) == portal) {
                continue;
            }
            Location middle = entity.getLocation().add(0, entity.getHeight() / 2, 0);
            if (!portal.contains(middle)) {
                continue;
            }
            RiftPortal target = portal.partner;
            if (PorticleGun.isWorldGuardEnabled()
                    && !WorldGuardIntegration.canUsePortal(entity, portal.feet, target.feet)) {
                continue;
            }
            Location before = entity.getLocation();
            if (entity.teleport(target.exit())) {
                entity.setVelocity(target.normal.clone().multiply(0.25));
                entity.setFallDistance(0);
                exitLocks.put(entity.getUniqueId(), target);
                target.feet.getWorld().playSound(target.centre(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.3f);
                if (entity instanceof Player player) {
                    storage.setBack(player.getUniqueId(), before);
                }
            }
        }
    }

    private static boolean canTravel(Entity entity) {
        return entity.isValid()
                && !(entity instanceof Display)
                && !(entity instanceof Hanging)
                && !(entity instanceof EnderDragon)
                && !(entity instanceof Wither)
                && !entity.isInsideVehicle()
                && entity.getPassengers().isEmpty();
    }

    /**
     * @return whether the player has a rift pair open
     */
    public boolean hasOpen(UUID owner) {
        return pairs.containsKey(owner);
    }

    Iterator<RiftPortal[]> openPairs() {
        return pairs.values().iterator();
    }
}
