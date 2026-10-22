package eu.nurkert.porticlegun.rift;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.util.ChunkLoader;
import eu.nurkert.porticlegun.util.WorldGuardIntegration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Using the interdimensional gun: attack (left click) opens a rift pair to the selected destination, use (right click)
 * opens the menu. Both work with a mouse, a controller and on touch screens; an empty tank is refilled from the
 * inventory on its own. While the gun is held, the action bar shows the
 * destination and the tank.
 */
public class RiftGunListener implements Listener {

    public static final String PERMISSION = "porticlegun.interdimensional";

    private static final long SEARCH_TIMEOUT_TICKS = 20L * 20;

    private final RiftModule module;
    private final Map<UUID, Long> lastRift = new HashMap<>();
    // player -> the search running for them; a search that took too long is dropped
    private final Map<UUID, Integer> searching = new HashMap<>();
    private int searches;
    // when a player last dropped an item or closed a menu
    private final Map<UUID, Long> lastSwingFree = new HashMap<>();
    private final BukkitTask actionBarTask;

    RiftGunListener(RiftModule module) {
        this.module = module;
        actionBarTask = Bukkit.getScheduler().runTaskTimer(module.plugin(), this::showActionBars, 20L, 20L);
    }

    void shutdown() {
        actionBarTask.cancel();
    }

    @EventHandler
    public void on(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !RiftItems.isGun(event.getItem())) {
            return;
        }
        Action action = event.getAction();
        boolean attack = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean use = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!attack && !use) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        // Bedrock clients swing the arm when dropping an item or closing a menu; that is no attack
        if (attack && System.currentTimeMillis() - lastSwingFree.getOrDefault(player.getUniqueId(), 0L) < 300) {
            return;
        }
        int slot = player.getInventory().getHeldItemSlot();
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(MessageManager.getMessage(player, "rift.no-permission"));
            return;
        }
        // attack opens a rift, use opens the menu; with no destination yet, attacking opens the menu too
        if (use || RiftItems.destination(event.getItem()) == null) {
            if (attack) {
                actionBar(player, MessageManager.getMessage(player, "rift.choose-destination"));
            }
            module.menu().open(player, slot);
        } else {
            fire(player, slot);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void on(BlockBreakEvent event) {
        // attacking with the gun opens rifts, it never breaks blocks (creative mode breaks them instantly)
        if (RiftItems.isGun(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void on(PlayerDropItemEvent event) {
        lastSwingFree.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void on(InventoryCloseEvent event) {
        lastSwingFree.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void on(PlayerDeathEvent event) {
        module.storage()
                .setDeath(event.getEntity().getUniqueId(), event.getEntity().getLocation());
    }

    @EventHandler
    public void on(PlayerQuitEvent event) {
        searching.remove(event.getPlayer().getUniqueId());
        lastSwingFree.remove(event.getPlayer().getUniqueId());
        module.portals().close(event.getPlayer().getUniqueId());
    }

    private void fire(Player player, int slot) {
        RiftSettings settings = module.settings();
        ItemStack gun = player.getInventory().getItem(slot);
        UUID id = player.getUniqueId();
        if (searching.containsKey(id)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRift.getOrDefault(id, 0L) < settings.cooldownMillis()) {
            deny(player, "rift.cooldown");
            return;
        }
        // while a rift compass attunes the gun, rifts cost no portal fluid (if enabled)
        boolean free = settings.attunedFreeRifts() && RiftItems.isAttuned(gun);
        if (!free && RiftItems.charges(gun) <= 0 && !refill(player, gun)) {
            player.playSound(player, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 1f, 0.6f);
            deny(player, "rift.tank-empty-no-fluid");
            return;
        }
        player.getInventory().setItem(slot, gun);
        RiftDestination destination = RiftItems.destination(gun);
        if (destination == null) {
            module.menu().open(player, slot);
            return;
        }

        Vector looking = player.getLocation().getDirection().setY(0);
        Vector direction = looking.lengthSquared() < 1e-6 ? new Vector(0, 0, 1) : looking.normalize();
        RiftPlacement.Spot entrance = RiftPlacement.entrance(player);
        if (entrance == null) {
            deny(player, "rift.no-room");
            return;
        }
        if (PorticleGun.isWorldGuardEnabled()
                && !WorldGuardIntegration.canCreatePortal(player, entrance.feet(), entrance.normal())) {
            deny(player, "rift.not-here");
            return;
        }

        // the entrance shows up right away, small and orange, until the far side is ready
        module.portals().charge(player, entrance.feet(), entrance.normal());
        Consumer<Location> openAt = target -> {
            if (target == null) {
                deny(player, "rift.no-safe-spot");
                return;
            }
            RiftPlacement.Spot exit = RiftPlacement.exit(target, direction);
            // load the far side first (in the background on Paper), so travellers never wait inside the rift
            ChunkLoader.whenLoaded(
                    module.plugin(),
                    exit.feet().getWorld(),
                    exit.feet().getBlockX(),
                    exit.feet().getBlockZ(),
                    () -> {
                        ItemStack current = player.getInventory().getItem(slot);
                        if (!player.isOnline()
                                || !RiftItems.isGun(current)
                                || (!free && RiftItems.charges(current) <= 0)) {
                            module.portals().cancelCharge(id);
                            return;
                        }
                        module.portals()
                                .open(
                                        player,
                                        entrance.feet(),
                                        entrance.normal(),
                                        exit.feet(),
                                        exit.normal(),
                                        settings.portalTicks(),
                                        settings.closeDistance());
                        if (!free) {
                            RiftItems.setCharges(current, RiftItems.charges(current) - 1, settings);
                        }
                        player.getInventory().setItem(slot, current);
                        lastRift.put(id, System.currentTimeMillis());
                    });
        };

        switch (destination.type()) {
            case WORLD -> {
                World world = Bukkit.getWorld(destination.world());
                if (world == null || settings.disabledWorlds().contains(world.getName())) {
                    deny(player, "rift.world-unavailable");
                    return;
                }
                // usually a spot is ready and the rift opens right away; otherwise search one now
                // (marked as a search right away, so a second click meanwhile does not open a second rift)
                Consumer<Location> found = search(player, "rift.no-safe-spot", openAt);
                module.readySpots().take(world, ready -> {
                    if (ready != null) {
                        found.accept(ready);
                        return;
                    }
                    player.playSound(player, Sound.BLOCK_BEACON_AMBIENT, 1f, 1.8f);
                    actionBar(player, MessageManager.getMessage(player, "rift.searching"));
                    module.finder().find(world, settings, found);
                });
            }
            case SPAWN -> {
                World world = Bukkit.getWorld(destination.world());
                if (world == null || settings.disabledWorlds().contains(world.getName())) {
                    deny(player, "rift.world-unavailable");
                    return;
                }
                openAt.accept(RiftPlacement.refine(world.getSpawnLocation().add(0.5, 0, 0.5)));
            }
            case HOME -> openStored(player, module.storage().home(id, destination.home()), "rift.home-missing", openAt);
            case BACK -> openStored(player, module.storage().back(id), "rift.back-missing", openAt);
            case DEATH -> openStored(player, module.storage().death(id), "rift.death-missing", openAt);
            case STRUCTURE -> {
                RiftStructure structure = RiftStructure.byId(destination.world());
                if (!RiftItems.isAttuned(gun)) {
                    deny(player, "rift.compass-faded");
                    return;
                }
                World world =
                        structure == null ? null : StructureLocator.worldFor(structure, player.getLocation(), settings);
                if (world == null) {
                    deny(player, "rift.world-unavailable");
                    return;
                }
                Consumer<Location> found = search(player, "rift.structure-not-found", openAt);
                player.playSound(player, Sound.ITEM_LODESTONE_COMPASS_LOCK, 1f, 1.4f);
                actionBar(
                        player,
                        MessageManager.getMessage(
                                player,
                                "rift.searching-structure",
                                Map.of("structure", RiftNames.structure(structure.id(), player))));
                module.structures()
                        .locate(
                                structure,
                                world,
                                StructureLocator.origin(world, player.getLocation()),
                                settings.structureSearchRadius(),
                                structureCentre -> {
                                    if (structureCentre == null) {
                                        found.accept(null);
                                    } else {
                                        module.finder().findNear(world, structureCentre, structure.landing(), found);
                                    }
                                });
            }
        }
    }

    /**
     * Marks a search as running for the player and gives it {@link #SEARCH_TIMEOUT_TICKS}: the returned callback opens
     * the rift if the search is still the player's current one, a search that took too long ends with a message.
     *
     * @param failedKey the message shown when nothing was found or the search took too long
     */
    private Consumer<Location> search(Player player, String failedKey, Consumer<Location> openAt) {
        UUID id = player.getUniqueId();
        int search = ++searches;
        searching.put(id, search);
        Bukkit.getScheduler()
                .runTaskLater(
                        module.plugin(),
                        () -> {
                            if (searching.remove(id, search) && player.isOnline()) {
                                deny(player, failedKey);
                            }
                        },
                        SEARCH_TIMEOUT_TICKS);
        return target -> {
            if (!searching.remove(id, search)) {
                return; // timed out, or the player left
            }
            if (target == null) {
                deny(player, failedKey);
                return;
            }
            openAt.accept(target);
        };
    }

    /**
     * Fills an empty tank with one portal fluid from the inventory, so nobody has to open the menu for it.
     *
     * @return whether the tank was refilled
     */
    private boolean refill(Player player, ItemStack gun) {
        if (!RiftItems.consumeFluid(player)) {
            return false;
        }
        RiftItems.setCharges(gun, module.settings().riftsPerFluid(), module.settings());
        player.playSound(player, Sound.ITEM_BOTTLE_EMPTY, 1f, 0.9f);
        player.playSound(player, Sound.BLOCK_BREWING_STAND_BREW, 0.6f, 1.6f);
        player.sendMessage(MessageManager.getMessage(player, "rift.tank-refilled"));
        return true;
    }

    private void openStored(Player player, Location location, String missingKey, Consumer<Location> openAt) {
        if (location == null) {
            deny(player, missingKey);
            return;
        }
        openAt.accept(RiftPlacement.refine(location));
    }

    /** Tells the player why no rift opens; a charging entrance collapses. */
    private void deny(Player player, String key) {
        module.portals().cancelCharge(player.getUniqueId());
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
        actionBar(player, MessageManager.getMessage(player, key));
    }

    private void showActionBars() {
        RiftSettings settings = module.settings();
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack gun = player.getInventory().getItemInMainHand();
            if (!RiftItems.isGun(gun) || searching.containsKey(player.getUniqueId())) {
                continue;
            }
            RiftDestination destination = RiftItems.destination(gun);
            int charges = RiftItems.charges(gun);
            actionBar(
                    player,
                    MessageManager.getMessage(
                            player,
                            "rift.actionbar",
                            Map.of(
                                    "destination",
                                            destination == null
                                                    ? MessageManager.getMessage(player, "rift.destination.none")
                                                    : RiftNames.describe(destination, player),
                                    "bar", RiftItems.tankBar(charges, settings.tankCapacity()),
                                    "charges", String.valueOf(charges),
                                    "capacity", String.valueOf(settings.tankCapacity()))));
        }
    }

    private static void actionBar(Player player, String text) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(TextComponent.fromLegacyText(text)));
    }
}
