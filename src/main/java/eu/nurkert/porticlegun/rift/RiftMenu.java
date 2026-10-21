package eu.nurkert.porticlegun.rift;

import eu.nurkert.porticlegun.builders.ItemBuilder;
import eu.nurkert.porticlegun.messages.MessageManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The menu of the interdimensional gun (sneak and use): choose a destination, manage homes and fill the tank.
 *
 * <p>Every action is a single plain click, so it works the same with a mouse, a controller and on touch screens
 * (Bedrock has no shift or right click in inventories). Shift and right clicks do exactly what a left click does.
 * The menu is recognised by its holder, never by its title, and no item can be taken out or put in.
 */
public class RiftMenu implements Listener {

    enum Page {
        MAIN,
        HOME,
        DELETE_HOME,
        ATLAS
    }

    /** Identifies an open rift menu and remembers which gun (by hotbar slot and id) it belongs to. */
    static final class Holder implements InventoryHolder {
        final Page page;
        final int home;
        final int slot;
        final String gunId;
        final Map<Integer, Runnable> actions = new HashMap<>();
        Inventory inventory;

        Holder(Page page, int home, int slot, String gunId) {
            this.page = page;
            this.home = home;
            this.slot = slot;
            this.gunId = gunId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // two rows of worlds (each with a random spot and its spawn), so up to eight worlds fit
    private static final int WORLD_ROW = 9;
    private static final int WORLDS_PER_ROW = 4;
    private static final int MAX_WORLDS = 8;
    private static final int SPECIAL_ROW = 27;
    private static final int HOME_ROW = 36;

    private final RiftModule module;

    public RiftMenu(RiftModule module) {
        this.module = module;
    }

    public void open(Player player, int slot) {
        ItemStack gun = player.getInventory().getItem(slot);
        if (!RiftItems.isGun(gun)) {
            return;
        }
        Holder holder = new Holder(Page.MAIN, -1, slot, RiftItems.gunId(gun));
        Inventory inventory = create(holder, 54, text(player, "rift.menu.title"));
        RiftSettings settings = module.settings();
        RiftDestination selected = RiftItems.destination(gun);

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, filler);
            inventory.setItem(45 + i, filler);
        }

        int charges = RiftItems.charges(gun);
        ItemStack status = gun.clone();
        inventory.setItem(4, status);

        button(
                holder,
                inventory,
                8,
                item(
                        Material.PAPER,
                        text(player, "rift.menu.fill.name"),
                        lines(
                                player,
                                "rift.menu.fill.lore",
                                Map.of(
                                        "rifts",
                                        String.valueOf(settings.riftsPerFluid()),
                                        "charges",
                                        String.valueOf(charges),
                                        "capacity",
                                        String.valueOf(settings.tankCapacity())))),
                () -> fillTank(player, holder));
        ItemStack fluidIcon = RiftItems.create(RiftItems.Type.PORTAL_FLUID, 1, settings);
        decorate(
                fluidIcon,
                text(player, "rift.menu.fill.name"),
                lines(
                        player,
                        "rift.menu.fill.lore",
                        Map.of(
                                "rifts",
                                String.valueOf(settings.riftsPerFluid()),
                                "charges",
                                String.valueOf(charges),
                                "capacity",
                                String.valueOf(settings.tankCapacity()))));
        inventory.setItem(8, fluidIcon);

        // the rift compass: insert one to attune the gun, which unlocks the rift atlas
        boolean attuned = RiftItems.isAttuned(gun);
        ItemStack compassIcon = RiftItems.create(RiftItems.Type.RIFT_COMPASS, 1, settings);
        Map<String, String> compassPlaceholders =
                Map.of("minutes", String.valueOf(settings.compassMillis() / 60_000), "time", RiftItems.remaining(gun));
        decorate(
                compassIcon,
                text(player, attuned ? "rift.menu.compass-active" : "rift.menu.compass.name", compassPlaceholders),
                lines(
                        player,
                        attuned ? "rift.menu.compass-active-lore" : "rift.menu.compass.lore",
                        compassPlaceholders));
        if (attuned) {
            glow(compassIcon);
        }
        button(holder, inventory, 0, compassIcon, () -> insertCompass(player, holder));
        ItemStack atlasIcon = item(
                attuned ? Material.FILLED_MAP : Material.MAP,
                text(player, attuned ? "rift.menu.atlas.name" : "rift.menu.atlas-locked"),
                lines(player, attuned ? "rift.menu.atlas.lore" : "rift.menu.atlas-locked-lore", Map.of()));
        if (selected != null && selected.type() == RiftDestination.Type.STRUCTURE) {
            glow(atlasIcon);
        }
        button(holder, inventory, 1, atlasIcon, () -> {
            if (RiftItems.isAttuned(player.getInventory().getItem(holder.slot))) {
                openAtlas(player, holder.slot);
            } else {
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
                player.sendMessage(text(player, "rift.not-attuned"));
            }
        });

        // worlds: a random spot or the spawn of every world that is not disabled
        inventory.setItem(WORLD_ROW, item(Material.FILLED_MAP, text(player, "rift.menu.worlds"), List.of()));
        int index = 0;
        for (World world : Bukkit.getWorlds()) {
            if (settings.disabledWorlds().contains(world.getName()) || index >= MAX_WORLDS) {
                continue;
            }
            int randomSlot = WORLD_ROW + (index / WORLDS_PER_ROW) * 9 + 1 + (index % WORLDS_PER_ROW) * 2;
            String name = RiftNames.world(world.getName(), player);
            destinationButton(
                    player,
                    holder,
                    inventory,
                    randomSlot,
                    worldIcon(world),
                    text(player, "rift.menu.world-random", Map.of("world", name)),
                    lines(player, "rift.menu.world-random-lore", Map.of("world", name)),
                    RiftDestination.world(world.getName()),
                    selected);
            destinationButton(
                    player,
                    holder,
                    inventory,
                    randomSlot + 1,
                    Material.LODESTONE,
                    text(player, "rift.menu.world-spawn", Map.of("world", name)),
                    lines(player, "rift.menu.world-spawn-lore", Map.of("world", name)),
                    RiftDestination.spawn(world.getName()),
                    selected);
            index++;
        }

        // back and last death
        inventory.setItem(SPECIAL_ROW, item(Material.CLOCK, text(player, "rift.menu.special"), List.of()));
        Location back = module.storage().back(player.getUniqueId());
        destinationButton(
                player,
                holder,
                inventory,
                SPECIAL_ROW + 1,
                Material.RECOVERY_COMPASS,
                text(player, "rift.destination.back"),
                lines(player, back == null ? "rift.menu.back-empty" : "rift.menu.back-lore", coordinates(back)),
                RiftDestination.back(),
                selected);
        Location death = module.storage().death(player.getUniqueId());
        destinationButton(
                player,
                holder,
                inventory,
                SPECIAL_ROW + 2,
                Material.SKELETON_SKULL,
                text(player, "rift.destination.death"),
                lines(player, death == null ? "rift.menu.death-empty" : "rift.menu.death-lore", coordinates(death)),
                RiftDestination.death(),
                selected);

        // homes: a set home opens its own page, an empty slot is set to the current position
        inventory.setItem(HOME_ROW, item(Material.RED_BED, text(player, "rift.menu.homes"), List.of()));
        for (int home = 0; home < settings.homes(); home++) {
            int number = home;
            Location location = module.storage().home(player.getUniqueId(), home);
            int homeSlot = HOME_ROW + 1 + home;
            Map<String, String> placeholders = new HashMap<>(coordinates(location));
            placeholders.put("home", String.valueOf(home + 1));
            if (location == null) {
                button(
                        holder,
                        inventory,
                        homeSlot,
                        item(
                                Material.LIGHT_GRAY_DYE,
                                text(player, "rift.menu.home-empty", placeholders),
                                lines(player, "rift.menu.home-empty-lore", placeholders)),
                        () -> {
                            setHome(player, number);
                            select(player, holder, RiftDestination.home(number));
                        });
            } else {
                ItemStack icon = item(
                        Material.LIME_BED,
                        text(player, "rift.menu.home", placeholders),
                        lines(player, "rift.menu.home-lore", placeholders));
                if (RiftDestination.home(home).equals(selected)) {
                    glow(icon);
                }
                button(holder, inventory, homeSlot, icon, () -> openHome(player, holder.slot, number));
            }
        }

        button(
                holder,
                inventory,
                49,
                item(Material.BARRIER, text(player, "rift.menu.close"), List.of()),
                player::closeInventory);
        inventory.setItem(
                45,
                item(Material.BOOK, text(player, "rift.menu.help"), lines(player, "rift.menu.help-lore", Map.of())));
        player.openInventory(inventory);
    }

    /**
     * The rift atlas: every structure kind as a destination, unlocked while the gun is attuned.
     */
    private void openAtlas(Player player, int slot) {
        ItemStack gun = player.getInventory().getItem(slot);
        if (!RiftItems.isGun(gun) || !RiftItems.isAttuned(gun)) {
            open(player, slot);
            return;
        }
        Holder holder = new Holder(Page.ATLAS, -1, slot, RiftItems.gunId(gun));
        Inventory inventory = create(holder, 54, text(player, "rift.menu.atlas-title"));
        ItemStack border = item(Material.LIME_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inventory.setItem(i, border);
            }
        }
        inventory.setItem(
                4,
                item(
                        Material.RECOVERY_COMPASS,
                        text(player, "rift.menu.compass-active", Map.of("time", RiftItems.remaining(gun))),
                        lines(player, "rift.menu.atlas-info", Map.of())));
        RiftDestination selected = RiftItems.destination(gun);
        int index = 0;
        for (RiftStructure structure : RiftStructure.values()) {
            int row = index / 7;
            int structureSlot = 10 + row * 9 + index % 7;
            index++;
            Map<String, String> placeholders = Map.of(
                    "structure", RiftNames.structure(structure.id(), player),
                    "dimension",
                            text(
                                    player,
                                    "rift.dimension."
                                            + structure.environment().name().toLowerCase()));
            RiftDestination destination = RiftDestination.structure(structure);
            ItemStack icon = item(
                    structure.icon(),
                    text(player, "rift.menu.structure", placeholders),
                    lines(player, "rift.menu.structure-lore", placeholders));
            if (destination.equals(selected)) {
                glow(icon);
                ItemMeta meta = icon.getItemMeta();
                List<String> lore = new ArrayList<>(meta.getLore());
                lore.add(text(player, "rift.menu.selected"));
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            button(holder, inventory, structureSlot, icon, () -> {
                select(player, holder, destination);
                openAtlas(player, holder.slot);
            });
        }
        button(
                holder,
                inventory,
                49,
                item(Material.ARROW, text(player, "rift.menu.back"), List.of()),
                () -> open(player, holder.slot));
        player.openInventory(inventory);
    }

    private void insertCompass(Player player, Holder holder) {
        RiftSettings settings = module.settings();
        ItemStack gun = player.getInventory().getItem(holder.slot);
        int compassSlot = -1;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (RiftItems.type(player.getInventory().getItem(i)) == RiftItems.Type.RIFT_COMPASS) {
                compassSlot = i;
                break;
            }
        }
        if (compassSlot < 0) {
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
            player.sendMessage(text(player, "rift.no-compass"));
            return;
        }
        ItemStack compass = player.getInventory().getItem(compassSlot);
        compass.setAmount(compass.getAmount() - 1);
        player.getInventory().setItem(compassSlot, compass.getAmount() > 0 ? compass : null);
        // a second compass adds to the remaining time
        long start = Math.max(System.currentTimeMillis(), RiftItems.attunedUntil(gun));
        RiftItems.attune(gun, start + settings.compassMillis(), settings);
        player.getInventory().setItem(holder.slot, gun);
        player.playSound(player, Sound.ITEM_LODESTONE_COMPASS_LOCK, 1f, 1f);
        player.playSound(player, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.6f);
        player.sendMessage(text(player, "rift.attuned", Map.of("time", RiftItems.remaining(gun))));
        openAtlas(player, holder.slot);
    }

    private void openHome(Player player, int slot, int home) {
        ItemStack gun = player.getInventory().getItem(slot);
        if (!RiftItems.isGun(gun)) {
            player.closeInventory();
            return;
        }
        Holder holder = new Holder(Page.HOME, home, slot, RiftItems.gunId(gun));
        Map<String, String> placeholders =
                new HashMap<>(coordinates(module.storage().home(player.getUniqueId(), home)));
        placeholders.put("home", String.valueOf(home + 1));
        Inventory inventory = create(holder, 27, text(player, "rift.menu.home-title", placeholders));
        button(
                holder,
                inventory,
                10,
                item(
                        Material.ENDER_PEARL,
                        text(player, "rift.menu.home-select"),
                        lines(player, "rift.menu.home-select-lore", placeholders)),
                () -> {
                    select(player, holder, RiftDestination.home(home));
                    open(player, holder.slot);
                });
        button(
                holder,
                inventory,
                13,
                item(
                        Material.COMPASS,
                        text(player, "rift.menu.home-set"),
                        lines(player, "rift.menu.home-set-lore", placeholders)),
                () -> {
                    setHome(player, home);
                    openHome(player, holder.slot, home);
                });
        button(
                holder,
                inventory,
                16,
                item(
                        Material.LAVA_BUCKET,
                        text(player, "rift.menu.home-delete"),
                        lines(player, "rift.menu.home-delete-lore", placeholders)),
                () -> openDelete(player, holder.slot, home));
        button(
                holder,
                inventory,
                22,
                item(Material.ARROW, text(player, "rift.menu.back"), List.of()),
                () -> open(player, holder.slot));
        player.openInventory(inventory);
    }

    private void openDelete(Player player, int slot, int home) {
        ItemStack gun = player.getInventory().getItem(slot);
        if (!RiftItems.isGun(gun)) {
            player.closeInventory();
            return;
        }
        Holder holder = new Holder(Page.DELETE_HOME, home, slot, RiftItems.gunId(gun));
        Map<String, String> placeholders = Map.of("home", String.valueOf(home + 1));
        Inventory inventory = create(holder, 27, text(player, "rift.menu.delete-title", placeholders));
        button(
                holder,
                inventory,
                11,
                item(Material.RED_CONCRETE, text(player, "rift.menu.delete-confirm", placeholders), List.of()),
                () -> {
                    module.storage().deleteHome(player.getUniqueId(), home);
                    ItemStack current = player.getInventory().getItem(holder.slot);
                    if (RiftItems.isGun(current) && RiftDestination.home(home).equals(RiftItems.destination(current))) {
                        RiftItems.setDestination(current, RiftDestination.back(), module.settings());
                    }
                    player.playSound(player, Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.2f);
                    player.sendMessage(text(player, "rift.home-deleted", placeholders));
                    open(player, holder.slot);
                });
        button(
                holder,
                inventory,
                15,
                item(Material.LIME_CONCRETE, text(player, "rift.menu.delete-cancel"), List.of()),
                () -> openHome(player, holder.slot, home));
        player.openInventory(inventory);
    }

    @EventHandler
    public void on(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        // nothing moves in or out of the menu, whatever kind of click it is
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getRawSlot() >= event.getInventory().getSize()) {
            return;
        }
        ItemStack gun = player.getInventory().getItem(holder.slot);
        if (!RiftItems.isGun(gun) || !holder.gunId.equals(RiftItems.gunId(gun))) {
            player.closeInventory(); // the gun was moved or swapped in the meantime
            return;
        }
        Runnable action = holder.actions.get(event.getRawSlot());
        if (action != null) {
            player.playSound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
            action.run();
        }
    }

    @EventHandler
    public void on(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void fillTank(Player player, Holder holder) {
        RiftSettings settings = module.settings();
        ItemStack gun = player.getInventory().getItem(holder.slot);
        int charges = RiftItems.charges(gun);
        if (charges >= settings.tankCapacity()) {
            player.sendMessage(text(player, "rift.tank-full"));
            return;
        }
        int fluidSlot = -1;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (RiftItems.type(player.getInventory().getItem(i)) == RiftItems.Type.PORTAL_FLUID) {
                fluidSlot = i;
                break;
            }
        }
        if (fluidSlot < 0) {
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
            player.sendMessage(text(player, "rift.no-fluid"));
            return;
        }
        ItemStack fluid = player.getInventory().getItem(fluidSlot);
        fluid.setAmount(fluid.getAmount() - 1);
        player.getInventory().setItem(fluidSlot, fluid.getAmount() > 0 ? fluid : null);
        player.getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE));
        RiftItems.setCharges(gun, charges + settings.riftsPerFluid(), settings);
        player.getInventory().setItem(holder.slot, gun);
        player.playSound(player, Sound.ITEM_BOTTLE_EMPTY, 1f, 0.9f);
        player.playSound(player, Sound.BLOCK_BREWING_STAND_BREW, 0.6f, 1.6f);
        open(player, holder.slot);
    }

    private void setHome(Player player, int home) {
        module.storage().setHome(player.getUniqueId(), home, player.getLocation());
        player.playSound(player, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.7f, 1.4f);
        player.sendMessage(text(player, "rift.home-set", Map.of("home", String.valueOf(home + 1))));
    }

    private void select(Player player, Holder holder, RiftDestination destination) {
        ItemStack gun = player.getInventory().getItem(holder.slot);
        RiftItems.setDestination(gun, destination, module.settings());
        player.getInventory().setItem(holder.slot, gun);
        player.sendMessage(text(
                player, "rift.destination-selected", Map.of("destination", RiftNames.describe(destination, player))));
    }

    private void destinationButton(
            Player player,
            Holder holder,
            Inventory inventory,
            int slot,
            Material material,
            String name,
            List<String> lore,
            RiftDestination destination,
            RiftDestination selected) {
        ItemStack icon = item(material, name, lore);
        if (destination.equals(selected)) {
            glow(icon);
            ItemMeta meta = icon.getItemMeta();
            List<String> withMarker = new ArrayList<>(meta.getLore());
            withMarker.add(text(player, "rift.menu.selected"));
            meta.setLore(withMarker);
            icon.setItemMeta(meta);
        }
        button(holder, inventory, slot, icon, () -> {
            select(player, holder, destination);
            open(player, holder.slot);
        });
    }

    private static Material worldIcon(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
    }

    private static Inventory create(Holder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.inventory = inventory;
        return inventory;
    }

    private static void button(Holder holder, Inventory inventory, int slot, ItemStack icon, Runnable action) {
        inventory.setItem(slot, icon);
        holder.actions.put(slot, action);
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemBuilder builder = new ItemBuilder(material).setName(name);
        if (!lore.isEmpty()) {
            builder.setLore(lore.toArray(new String[0]));
        }
        ItemStack item = builder.build();
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private static void decorate(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private static void glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }

    private static Map<String, String> coordinates(Location location) {
        if (location == null) {
            return Map.of();
        }
        return Map.of(
                "world", RiftNames.world(location.getWorld().getName(), null),
                "x", String.valueOf(location.getBlockX()),
                "y", String.valueOf(location.getBlockY()),
                "z", String.valueOf(location.getBlockZ()));
    }

    private static String text(Player player, String key) {
        return MessageManager.getMessage(player, key);
    }

    private static String text(Player player, String key, Map<String, String> placeholders) {
        return MessageManager.getMessage(player, key, placeholders);
    }

    private static List<String> lines(Player player, String key, Map<String, String> placeholders) {
        List<String> lines = new ArrayList<>();
        for (String line : MessageManager.getMessage(player, key, placeholders).split("\n")) {
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }
}
