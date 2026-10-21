package eu.nurkert.porticlegun.rift;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.util.WorldGuardIntegration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Using the interdimensional gun: use opens a rift pair to the selected destination, sneak and use opens the menu.
 * Both work with a mouse, a controller and on touch screens. While the gun is held, the action bar shows the
 * destination and the tank.
 */
public class RiftGunListener implements Listener {

    public static final String PERMISSION = "porticlegun.interdimensional";

    private final RiftModule module;
    private final Map<UUID, Long> lastRift = new HashMap<>();
    private final Set<UUID> searching = new HashSet<>();
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
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        int slot = player.getInventory().getHeldItemSlot();
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(MessageManager.getMessage(player, "rift.no-permission"));
            return;
        }
        if (player.isSneaking()) {
            module.menu().open(player, slot);
        } else {
            fire(player, slot);
        }
    }

    @EventHandler
    public void on(PlayerDeathEvent event) {
        module.storage()
                .setDeath(event.getEntity().getUniqueId(), event.getEntity().getLocation());
    }

    @EventHandler
    public void on(PlayerQuitEvent event) {
        searching.remove(event.getPlayer().getUniqueId());
        module.portals().close(event.getPlayer().getUniqueId());
    }

    private void fire(Player player, int slot) {
        RiftSettings settings = module.settings();
        ItemStack gun = player.getInventory().getItem(slot);
        UUID id = player.getUniqueId();
        if (searching.contains(id)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRift.getOrDefault(id, 0L) < settings.cooldownMillis()) {
            deny(player, "rift.cooldown");
            return;
        }
        // while a rift compass attunes the gun, rifts cost no portal fluid (if enabled)
        boolean free = settings.attunedFreeRifts() && RiftItems.isAttuned(gun);
        if (!free && RiftItems.charges(gun) <= 0) {
            player.playSound(player, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 1f, 0.6f);
            deny(player, "rift.tank-empty");
            return;
        }
        RiftDestination destination = RiftItems.destination(gun);
        if (destination == null) {
            deny(player, "rift.no-destination");
            return;
        }

        Vector looking = player.getLocation().getDirection().setY(0);
        Vector direction = looking.lengthSquared() < 1e-6 ? new Vector(0, 0, 1) : looking.normalize();
        RiftPlacement.Spot entrance = RiftPlacement.entrance(player.getLocation(), direction);
        if (entrance == null) {
            deny(player, "rift.no-room");
            return;
        }
        if (PorticleGun.isWorldGuardEnabled()
                && !WorldGuardIntegration.canCreatePortal(player, entrance.feet(), entrance.normal())) {
            deny(player, "rift.not-here");
            return;
        }

        Consumer<Location> openAt = target -> {
            searching.remove(id);
            if (!player.isOnline()) {
                return;
            }
            ItemStack current = player.getInventory().getItem(slot);
            if (!RiftItems.isGun(current) || (!free && RiftItems.charges(current) <= 0)) {
                return;
            }
            if (target == null) {
                deny(player, "rift.no-safe-spot");
                return;
            }
            RiftPlacement.Spot exit = RiftPlacement.exit(target, direction);
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
        };

        switch (destination.type()) {
            case WORLD -> {
                World world = Bukkit.getWorld(destination.world());
                if (world == null || settings.disabledWorlds().contains(world.getName())) {
                    deny(player, "rift.world-unavailable");
                    return;
                }
                searching.add(id);
                player.playSound(player, Sound.BLOCK_BEACON_AMBIENT, 1f, 1.8f);
                actionBar(player, MessageManager.getMessage(player, "rift.searching"));
                module.finder().find(world, settings, openAt);
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
                searching.add(id);
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
                                found -> {
                                    if (found == null) {
                                        searching.remove(id);
                                        deny(player, "rift.structure-not-found");
                                        return;
                                    }
                                    module.finder().findNear(world, found, structure.landing(), openAt);
                                });
            }
        }
    }

    private void openStored(Player player, Location location, String missingKey, Consumer<Location> openAt) {
        if (location == null) {
            deny(player, missingKey);
            return;
        }
        openAt.accept(RiftPlacement.refine(location));
    }

    private void deny(Player player, String key) {
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
        actionBar(player, MessageManager.getMessage(player, key));
    }

    private void showActionBars() {
        RiftSettings settings = module.settings();
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack gun = player.getInventory().getItemInMainHand();
            if (!RiftItems.isGun(gun) || searching.contains(player.getUniqueId())) {
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
