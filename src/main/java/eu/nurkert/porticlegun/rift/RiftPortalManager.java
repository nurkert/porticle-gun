package eu.nurkert.porticlegun.rift;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.util.BedrockSupport;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Opens and runs the green rifts of the interdimensional gun: always a pair, one in front of the player and one at the
 * destination, both walkable in both directions until they close again.
 *
 * <p>A rift is drawn from particles only, so it needs no resource pack: a crisp, wobbling lime rim, a dark green band
 * and spiral body, and light streams spiralling into the bright core (Bedrock gets the same shape from dust only). It opens and closes by growing from and shrinking to its centre. While a rift is open, the chunks of both
 * portals stay loaded, so travellers arrive without waiting and the far side keeps working.
 */
public class RiftPortalManager implements Listener {

    // a Rick and Morty portal: a crisp lime rim with a pale highlight, a dark green band and body, light streams
    // spiralling into a bright core
    private static final Color RIM_COLOUR = Color.fromRGB(0xC8F750);
    private static final Particle.DustOptions RIM = new Particle.DustOptions(RIM_COLOUR, 1.3f);
    private static final Particle.DustOptions HIGHLIGHT = new Particle.DustOptions(Color.fromRGB(0xF4FFE0), 0.9f);
    private static final Particle.DustOptions BAND = new Particle.DustOptions(Color.fromRGB(0x1F8A2E), 1.6f);
    private static final Particle.DustOptions BODY = new Particle.DustOptions(Color.fromRGB(0x2A9A36), 1.7f);
    private static final Particle.DustOptions CORE = new Particle.DustOptions(Color.fromRGB(0xF2FFC4), 1.8f);
    private static final Color[] STREAM_COLOURS = {RIM_COLOUR, Color.fromRGB(0xE6FFB0), Color.fromRGB(0x8FE04A)};
    // Bedrock has no trail particles and draws dust slower: fewer, bigger particles
    private static final Particle.DustOptions BEDROCK_RIM = new Particle.DustOptions(RIM_COLOUR, 2.0f);
    private static final Particle.DustOptions BEDROCK_BAND = new Particle.DustOptions(Color.fromRGB(0x1F8A2E), 2.0f);
    private static final Particle.DustOptions BEDROCK_ARM = new Particle.DustOptions(Color.fromRGB(0xB6F04E), 1.6f);
    private static final int OPEN_TICKS = 10;
    // particles are only sent while a player is this close, the client would not show them farther away anyway
    private static final double VIEW_DISTANCE = 48;
    private static final double LEAVE_DISTANCE = 2.5;
    private static final int WALK_AWAY_TICKS = 100;

    private final PorticleGun plugin;
    private final RiftStorage storage;
    private final Map<UUID, RiftPortal[]> pairs = new HashMap<>();
    // block -> the open portals covering it
    private final Map<CellKey, List<RiftPortal>> cells = new HashMap<>();
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
        index();
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
        if (pair != null) {
            index();
        }
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
        for (RiftPortal[] pair : new ArrayList<>(pairs.values())) {
            for (RiftPortal portal : pair) {
                if (!loaded(portal)) {
                    continue;
                }
                particles(portal, Math.min(1.0, (tick - portal.openedAt) / (double) OPEN_TICKS));
                if (tick % 2 == 0) {
                    travelOthers(portal);
                }
            }
        }
        closing.removeIf(portal -> {
            if (tick - portal.closedAt <= OPEN_TICKS) {
                return false;
            }
            releaseChunk(portal);
            return true;
        });
        for (RiftPortal portal : closing) {
            if (loaded(portal)) {
                particles(portal, 1.0 - (tick - portal.closedAt) / (double) OPEN_TICKS);
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
            double distance = closeDistances.getOrDefault(owner, 24.0);
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
     * Draws one frame of the rift (every tick, half of the points each). Java players get a crisp double rim, a dark
     * band and spiral body, and trail particles that stream from the rim into the core along a spiral, each for a fixed time; Bedrock
     * players, whose clients know no trail particles, get the same shape from fewer, bigger dust particles. size grows
     * from 0 to 1 while the rift opens and shrinks back while it closes.
     */
    private void particles(RiftPortal portal, double size) {
        if (size <= 0) {
            return;
        }
        Location centre = portal.centre();
        List<Player> java = new ArrayList<>();
        List<Player> bedrock = new ArrayList<>();
        for (Player player : centre.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(centre) < VIEW_DISTANCE * VIEW_DISTANCE) {
                (BedrockSupport.isBedrock(player) ? bedrock : java).add(player);
            }
        }
        if (!java.isEmpty()) {
            new Frame(portal, centre, size, tick, (int) (tick % 2)).java(java);
        }
        // Bedrock every second tick, again alternating between the two halves of the points
        if (!bedrock.isEmpty() && tick % 2 == 0) {
            new Frame(portal, centre, size, tick, (int) (tick / 2 % 2)).bedrock(bedrock);
        }
    }

    /** One frame of a rift: positions on its wobbling oval, in the portal's plane. */
    private static final class Frame {
        private final RiftPortal portal;
        private final Location centre;
        private final double halfWidth;
        private final double halfHeight;
        private final double t;
        private final int half;

        Frame(RiftPortal portal, Location centre, double size, long tick, int half) {
            this.portal = portal;
            this.centre = centre;
            this.halfWidth = RiftPortal.HALF_WIDTH * size;
            this.halfHeight = RiftPortal.HEIGHT / 2 * size;
            this.t = tick;
            this.half = half;
        }

        void java(List<Player> viewers) {
            ring(viewers, 28, 1.0, t * 0.03, 0, RIM);
            ring(viewers, 12, 1.045, t * 0.03, 0.5, HIGHLIGHT);
            ring(viewers, 20, 0.88, -t * 0.05, 0, BAND);
            arms(viewers, 5, BODY);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 6; i++) {
                // a stream starts just inside the rim and ends near the core, a third of a turn further
                double angle = random.nextDouble(Math.PI * 2);
                double start = random.nextDouble(0.7, 0.84);
                Location target = at(angle + 1.6, 0.12);
                Particle.Trail trail =
                        new Particle.Trail(target, STREAM_COLOURS[i % STREAM_COLOURS.length], random.nextInt(16, 25));
                spawn(viewers, Particle.TRAIL, at(angle, start), trail);
            }
            if (half == 0) {
                spawn(viewers, Particle.DUST, at(0, 0), CORE);
            }
        }

        void bedrock(List<Player> viewers) {
            ring(viewers, 20, 1.0, t * 0.03, 0, BEDROCK_RIM);
            ring(viewers, 14, 0.86, -t * 0.05, 0, BEDROCK_BAND);
            arms(viewers, 4, BEDROCK_ARM);
            spawn(viewers, Particle.DUST, at(0, 0), CORE);
        }

        /**
         * Every frame draws every second point of a ring, the next frame the others: the same particles per second as
         * drawing all of them every other tick, but spread evenly, so the rift does not pulse.
         */
        private void ring(
                List<Player> viewers,
                int points,
                double radius,
                double turn,
                double offset,
                Particle.DustOptions dust) {
            for (int i = half; i < points; i += 2) {
                spawn(viewers, Particle.DUST, at((i + offset) * Math.PI * 2 / points + turn, radius), dust);
            }
        }

        /** Three spiral arms whose points flow steadily inwards towards the core. */
        private void arms(List<Player> viewers, int points, Particle.DustOptions dust) {
            double flow = (t * 0.04) % 1;
            for (int arm = 0; arm < 3; arm++) {
                for (int j = half; j < points; j += 2) {
                    double reach = 0.15 + 0.65 * (j + 1 - flow) / points;
                    spawn(viewers, Particle.DUST, at(arm * Math.PI * 2 / 3 + 4.6 * reach - t * 0.15, reach), dust);
                }
            }
        }

        /** The point at the given angle, radius 1 being the wobbling rim. */
        private Location at(double angle, double radius) {
            double wobble = radius
                    * (1
                            + 0.055 * Math.sin(3 * angle + t * 0.15)
                            + 0.04 * Math.sin(7 * angle - t * 0.23)
                            + 0.025 * Math.sin(13 * angle + t * 0.41));
            double side = Math.cos(angle) * halfWidth * wobble;
            return new Location(
                    centre.getWorld(),
                    centre.getX() + portal.right.getX() * side,
                    centre.getY() + Math.sin(angle) * halfHeight * wobble,
                    centre.getZ() + portal.right.getZ() * side);
        }

        private static <T> void spawn(List<Player> viewers, Particle particle, Location at, T data) {
            for (Player viewer : viewers) {
                viewer.spawnParticle(particle, at, 1, 0, 0, 0, 0, data);
            }
        }
    }

    private static boolean loaded(RiftPortal portal) {
        return portal.feet.getWorld().isChunkLoaded(portal.feet.getBlockX() >> 4, portal.feet.getBlockZ() >> 4);
    }

    /**
     * Rebuilds the grid of blocks each open portal covers. Players are checked against it only when their body moves
     * into another block, with one hash lookup instead of a loop over all portals.
     */
    private void index() {
        cells.clear();
        for (RiftPortal[] pair : pairs.values()) {
            for (RiftPortal portal : pair) {
                Location centre = portal.centre();
                for (double side = -RiftPortal.HALF_WIDTH; side <= RiftPortal.HALF_WIDTH; side += 0.5) {
                    for (double up = -RiftPortal.HEIGHT / 2; up <= RiftPortal.HEIGHT / 2; up += 0.5) {
                        for (double depth = -0.45; depth <= 0.45; depth += 0.45) {
                            Location cell = centre.clone()
                                    .add(portal.right.clone().multiply(side))
                                    .add(portal.normal.clone().multiply(depth))
                                    .add(0, up, 0);
                            List<RiftPortal> here = cells.computeIfAbsent(cellKey(cell), key -> new ArrayList<>(1));
                            if (!here.contains(portal)) {
                                here.add(portal);
                            }
                        }
                    }
                }
            }
        }
    }

    private static CellKey cellKey(Location location) {
        return new CellKey(
                location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private record CellKey(UUID world, int x, int y, int z) {}

    @EventHandler(ignoreCancelled = true)
    public void on(PlayerMoveEvent event) {
        if (cells.isEmpty() || event.getTo() == null) {
            return;
        }
        Player player = event.getPlayer();
        double middle = player.getHeight() / 2;
        Location from = event.getFrom().clone().add(0, middle, 0);
        Location to = event.getTo().clone().add(0, middle, 0);
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && from.getWorld() == to.getWorld()) {
            return;
        }
        List<RiftPortal> here = cells.get(cellKey(to));
        if (here == null) {
            return;
        }
        for (RiftPortal portal : here) {
            if (exitLocks.get(player.getUniqueId()) != portal && portal.contains(to) && canTravel(player)) {
                // after the event the player would be put back to where the event says; move them next tick
                Bukkit.getScheduler().runTask(plugin, () -> travel(player, portal));
                return;
            }
        }
    }

    /** Mobs, items and arrows: they have no move event, so the few entities next to each portal are checked. */
    private void travelOthers(RiftPortal portal) {
        Location centre = portal.centre();
        for (Entity entity : centre.getWorld().getNearbyEntities(centre, 1.6, 1.8, 1.6)) {
            if (entity instanceof Player || !canTravel(entity) || exitLocks.get(entity.getUniqueId()) == portal) {
                continue;
            }
            if (portal.contains(entity.getLocation().add(0, entity.getHeight() / 2, 0))) {
                travel(entity, portal);
            }
        }
    }

    private void travel(Entity entity, RiftPortal portal) {
        if (!entity.isValid()
                || !isOpen(portal)
                || exitLocks.get(entity.getUniqueId()) == portal
                || !portal.contains(entity.getLocation().add(0, entity.getHeight() / 2, 0))) {
            return; // e.g. two move events in one tick: the first already took the player through
        }
        RiftPortal target = portal.partner;
        if (PorticleGun.isWorldGuardEnabled()
                && !WorldGuardIntegration.canUsePortal(entity, portal.feet, target.feet)) {
            return;
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

    private boolean isOpen(RiftPortal portal) {
        RiftPortal[] pair = pairs.get(portal.owner);
        return pair != null && (pair[0] == portal || pair[1] == portal);
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
