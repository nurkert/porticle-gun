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
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Opens and runs the green rifts of the interdimensional gun: always a pair, one in front of the player and one at the
 * destination, both walkable in both directions until they close again.
 *
 * <p>Java players see an item display with the animated swirl from the resource pack that opens up and wobbles; the
 * client interpolates the motion, so it costs the server almost nothing. Bedrock players see green particles only,
 * the display is hidden from them because Geyser cannot show it properly.
 */
public class RiftPortalManager implements Listener {

    private static final Particle.DustOptions GREEN = new Particle.DustOptions(Color.fromRGB(0x39FF5A), 1.4f);
    private static final Particle.DustOptions LIGHT_GREEN = new Particle.DustOptions(Color.fromRGB(0xB6FF7A), 1.0f);
    private static final float WIDTH = (float) (RiftPortal.HALF_WIDTH * 2.2);
    private static final float HEIGHT = (float) (RiftPortal.HEIGHT * 1.05);
    private static final int OPEN_TICKS = 8;
    private static final int WOBBLE_TICKS = 10;
    private static final double LEAVE_DISTANCE = 2.5;

    private final PorticleGun plugin;
    private final RiftStorage storage;
    private final Map<UUID, RiftPortal[]> pairs = new HashMap<>();
    // entity -> portal it just came out of; it cannot go back through until it stepped away
    private final Map<UUID, RiftPortal> exitLocks = new HashMap<>();
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
     * @param lifetimeTicks how long both portals stay open
     */
    public void open(Player owner, Location entranceFeet, Vector entranceNormal, Location exitFeet, Vector exitNormal,
            int lifetimeTicks) {
        close(owner.getUniqueId());
        RiftPortal entrance = new RiftPortal(owner.getUniqueId(), entranceFeet, entranceNormal);
        RiftPortal exit = new RiftPortal(owner.getUniqueId(), exitFeet, exitNormal);
        entrance.partner = exit;
        exit.partner = entrance;
        for (RiftPortal portal : List.of(entrance, exit)) {
            portal.display = spawnDisplay(portal);
            World world = portal.feet.getWorld();
            world.playSound(portal.centre(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 1.4f);
            world.playSound(portal.centre(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 0.8f, 1.2f);
        }
        pairs.put(owner.getUniqueId(), new RiftPortal[] {entrance, exit});
        UUID id = owner.getUniqueId();
        RiftPortal[] opened = pairs.get(id);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pairs.get(id) == opened) {
                close(id);
            }
        }, lifetimeTicks);
    }

    public void close(UUID owner) {
        RiftPortal[] pair = pairs.remove(owner);
        if (pair == null) {
            return;
        }
        for (RiftPortal portal : pair) {
            portal.feet.getWorld().playSound(portal.centre(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.6f);
            exitLocks.values().removeIf(locked -> locked == portal);
            ItemDisplay display = portal.display;
            if (display == null || !display.isValid()) {
                continue;
            }
            animate(display, 0f, 0f, 0f, OPEN_TICKS);
            Bukkit.getScheduler().runTaskLater(plugin, display::remove, OPEN_TICKS + 1);
        }
    }

    public void shutdown() {
        task.cancel();
        for (RiftPortal[] pair : new ArrayList<>(pairs.values())) {
            for (RiftPortal portal : pair) {
                if (portal.display != null) {
                    portal.display.remove();
                }
            }
        }
        pairs.clear();
        exitLocks.clear();
    }

    private ItemDisplay spawnDisplay(RiftPortal portal) {
        Location location = portal.centre();
        location.setDirection(portal.normal);
        location.setPitch(0);
        ItemStack swirl = new ItemStack(Material.PAPER);
        ItemMeta meta = swirl.getItemMeta();
        meta.setItemModel(new NamespacedKey(plugin, "rift_portal"));
        swirl.setItemMeta(meta);
        ItemDisplay display = location.getWorld().spawn(location, ItemDisplay.class, spawned -> {
            spawned.setItemStack(swirl);
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setPersistent(false);
            spawned.setTransformation(transformation(0f, 0f, 0f));
        });
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (BedrockSupport.isBedrock(player)) {
                player.hideEntity(plugin, display);
            }
        }
        // open up on the next tick, so the client interpolates from size zero
        Bukkit.getScheduler().runTaskLater(plugin, () -> animate(display, WIDTH, HEIGHT, 0f, OPEN_TICKS), 1L);
        return display;
    }

    private static Transformation transformation(float width, float height, float tilt) {
        return new Transformation(
                new Vector3f(),
                new AxisAngle4f((float) Math.toRadians(tilt), 0, 0, 1),
                new Vector3f(width, height, 1f),
                new AxisAngle4f());
    }

    private static void animate(ItemDisplay display, float width, float height, float tilt, int ticks) {
        if (!display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation(width, height, tilt));
    }

    private void tick() {
        tick++;
        exitLocks.entrySet().removeIf(entry -> {
            Entity entity = Bukkit.getEntity(entry.getKey());
            return entity == null
                    || !entity.isValid()
                    || !entity.getWorld().equals(entry.getValue().feet.getWorld())
                    || entity.getLocation().distance(entry.getValue().feet) > LEAVE_DISTANCE;
        });
        for (RiftPortal[] pair : new ArrayList<>(pairs.values())) {
            for (RiftPortal portal : pair) {
                if (!portal.feet.getChunk().isLoaded()) {
                    continue;
                }
                if (tick % 2 == 0) {
                    particles(portal);
                }
                if (tick % WOBBLE_TICKS == 0 && tick > OPEN_TICKS && portal.display != null) {
                    // wobble: alternate between a wider and a taller oval with a slight tilt
                    boolean phase = (tick / WOBBLE_TICKS) % 2 == 0;
                    animate(portal.display, phase ? WIDTH * 1.06f : WIDTH * 0.96f,
                            phase ? HEIGHT * 0.95f : HEIGHT * 1.04f, phase ? 4f : -4f, WOBBLE_TICKS);
                }
                travel(portal);
            }
        }
    }

    private void particles(RiftPortal portal) {
        World world = portal.feet.getWorld();
        Location centre = portal.centre();
        double offset = tick * 0.15;
        for (int i = 0; i < 10; i++) {
            double angle = offset + i * Math.PI * 2 / 10;
            Location rim = centre.clone()
                    .add(portal.right.clone().multiply(Math.cos(angle) * RiftPortal.HALF_WIDTH * 1.05))
                    .add(0, Math.sin(angle) * RiftPortal.HEIGHT / 2 * 1.05, 0);
            world.spawnParticle(Particle.DUST, rim, 1, 0, 0, 0, 0, GREEN);
        }
        world.spawnParticle(Particle.DUST, centre, 3, 0.35, 0.6, 0.05, 0, LIGHT_GREEN);
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

    @EventHandler
    public void on(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!BedrockSupport.isBedrock(player)) {
            return;
        }
        for (RiftPortal[] pair : pairs.values()) {
            for (RiftPortal portal : pair) {
                if (portal.display != null) {
                    player.hideEntity(plugin, portal.display);
                }
            }
        }
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
