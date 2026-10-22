package eu.nurkert.porticlegun.rift;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.util.WorldGuardIntegration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
 * thick, wobbling lime rim with white bubbles, a dark green body and three light spiral arms turning towards the bright
 * core. It opens and closes by growing from and shrinking to its centre. While a rift is open, the chunks of both
 * portals stay loaded, so travellers arrive without waiting and the far side keeps working.
 */
public class RiftPortalManager implements Listener {

    // the colours of a Rick and Morty portal: a thick, glowing lime rim, a dark green body and light swirl arms
    private static final Particle.DustOptions RIM = new Particle.DustOptions(Color.fromRGB(0xD4FF5A), 2.0f);
    private static final Particle.DustOptions BUBBLE = new Particle.DustOptions(Color.fromRGB(0xF6FFE8), 2.0f);
    private static final Particle.DustOptions GLOW = new Particle.DustOptions(Color.fromRGB(0x9BE33F), 1.6f);
    private static final Particle.DustOptions SWIRL = new Particle.DustOptions(Color.fromRGB(0xC4F55E), 1.25f);
    private static final Particle.DustOptions BODY = new Particle.DustOptions(Color.fromRGB(0x1C7A2B), 1.9f);
    private static final Particle.DustOptions BODY_LIGHT = new Particle.DustOptions(Color.fromRGB(0x2FA83A), 1.9f);
    private static final Particle.DustOptions CORE = new Particle.DustOptions(Color.fromRGB(0xF2FFC4), 1.7f);
    private static final int OPEN_TICKS = 10;
    private static final int RIM_POINTS = 40;
    private static final int GLOW_POINTS = 32;
    private static final int ARMS = 3;
    private static final int ARM_POINTS = 10;
    private static final int BODY_POINTS = 12;
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
            world.addPluginChunkTicket(portal.feet.getBlockX() >> 4, portal.feet.getBlockZ() >> 4, plugin);
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

    /**
     * Lets the chunk of a closed portal unload again, unless another rift (or the menu, at 0 0) still needs it.
     */
    private void releaseChunk(RiftPortal closed) {
        World world = closed.feet.getWorld();
        int chunkX = closed.feet.getBlockX() >> 4;
        int chunkZ = closed.feet.getBlockZ() >> 4;
        if (chunkX == 0 && chunkZ == 0) {
            return;
        }
        List<RiftPortal> others = new ArrayList<>(closing);
        pairs.values().forEach(pair -> others.addAll(List.of(pair)));
        for (RiftPortal other : others) {
            if (other != closed
                    && other.feet.getWorld().equals(world)
                    && other.feet.getBlockX() >> 4 == chunkX
                    && other.feet.getBlockZ() >> 4 == chunkZ) {
                return;
            }
        }
        world.removePluginChunkTicket(chunkX, chunkZ, plugin);
    }

    public void shutdown() {
        task.cancel();
        // the server drops all chunk tickets of a disabled plugin itself
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
        closing.removeIf(portal -> {
            if (tick - portal.closedAt <= OPEN_TICKS) {
                return false;
            }
            releaseChunk(portal);
            return true;
        });
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
     * Draws one frame of the rift (every second tick, about 110 particles; dust lingers for a moment, so the frames
     * blend into a swirl). size grows from 0 to 1 while it opens and shrinks back while it closes.
     */
    private void particles(RiftPortal portal, double size) {
        Location centre = portal.centre();
        if (size <= 0) {
            return;
        }
        List<Player> viewers = viewers(centre);
        if (viewers.isEmpty()) {
            return;
        }
        double t = tick;
        double halfWidth = RiftPortal.HALF_WIDTH * size;
        double halfHeight = RiftPortal.HEIGHT / 2 * size;
        // the rim: a blobby oval whose edge wobbles in three waves, with white bubbles running around it
        for (int i = 0; i < RIM_POINTS; i++) {
            double angle = i * Math.PI * 2 / RIM_POINTS + t * 0.03;
            double radius = blob(angle, t);
            boolean bubble = (i + tick / 4) % 7 == 0;
            spawn(viewers, portal, centre, angle, halfWidth * radius, halfHeight * radius, bubble ? BUBBLE : RIM);
        }
        // a second, darker band just inside, turning the other way
        for (int i = 0; i < GLOW_POINTS; i++) {
            double angle = i * Math.PI * 2 / GLOW_POINTS - t * 0.05 + 0.1;
            double radius = blob(angle, t) * 0.87;
            spawn(viewers, portal, centre, angle, halfWidth * radius, halfHeight * radius, GLOW);
        }
        // three light spiral arms turning towards the core
        for (int arm = 0; arm < ARMS; arm++) {
            for (int j = 0; j < ARM_POINTS; j++) {
                double reach = 0.1 + 0.72 * (j + (tick / 2 % 2) * 0.5) / ARM_POINTS;
                double angle = arm * Math.PI * 2 / ARMS + 4.6 * reach - t * 0.15;
                double radius = blob(angle, t) * reach;
                spawn(viewers, portal, centre, angle, halfWidth * radius, halfHeight * radius, SWIRL);
            }
        }
        // the dark green body between the arms, scattered anew every frame
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < BODY_POINTS; i++) {
            double angle = random.nextDouble(Math.PI * 2);
            double radius = Math.sqrt(random.nextDouble()) * 0.8 * blob(angle, t);
            spawn(
                    viewers,
                    portal,
                    centre,
                    angle,
                    halfWidth * radius,
                    halfHeight * radius,
                    i % 3 == 0 ? BODY_LIGHT : BODY);
        }
        for (int i = 0; i < 2; i++) {
            spawn(viewers, portal, centre, random.nextDouble(Math.PI * 2), 0.07 * size, 0.08 * size, CORE);
        }
    }

    private static double blob(double angle, double t) {
        return 1
                + 0.055 * Math.sin(3 * angle + t * 0.15)
                + 0.04 * Math.sin(7 * angle - t * 0.23)
                + 0.025 * Math.sin(13 * angle + t * 0.41);
    }

    /** One particle on the ellipse with the given half axes, at the given angle. */
    private static void spawn(
            List<Player> viewers,
            RiftPortal portal,
            Location centre,
            double angle,
            double halfWidth,
            double halfHeight,
            Particle.DustOptions colour) {
        double side = Math.cos(angle) * halfWidth;
        double x = centre.getX() + portal.right.getX() * side;
        double y = centre.getY() + Math.sin(angle) * halfHeight;
        double z = centre.getZ() + portal.right.getZ() * side;
        for (Player viewer : viewers) {
            viewer.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, colour);
        }
    }

    private static List<Player> viewers(Location centre) {
        List<Player> viewers = new ArrayList<>();
        for (Player player : centre.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(centre) < VIEW_DISTANCE * VIEW_DISTANCE) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    private static boolean loaded(RiftPortal portal) {
        return portal.feet.getWorld().isChunkLoaded(portal.feet.getBlockX() >> 4, portal.feet.getBlockZ() >> 4);
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
