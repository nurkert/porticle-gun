package eu.nurkert.porticlegun.rift;

import eu.nurkert.porticlegun.builders.BannerBuilder;
import eu.nurkert.porticlegun.builders.ItemBuilder;
import eu.nurkert.porticlegun.messages.MessageManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
        int atlasSlot = -1;

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

    // the only free slot of the main page, where portal fluid or a rift compass is put, and its load button
    static final int INPUT_SLOT = 6;
    private static final int LOAD_SLOT = 7;
    private static final int WORLDS_PER_ROW = 4;
    private static final int MAX_WORLDS = 8;

    private final RiftModule module;

    public RiftMenu(RiftModule module) {
        this.module = module;
    }

    /**
     * The main page, top to bottom: the gun with its tank between the rift compass and the portal fluid, then one row
     * per kind of destination (worlds, journeys, homes), each led by a label. The menu only grows a second world row
     * when there are more than four worlds.
     */
    public void open(Player player, int slot) {
        ItemStack gun = player.getInventory().getItem(slot);
        if (!RiftItems.isGun(gun)) {
            return;
        }
        RiftSettings settings = module.settings();
        RiftDestination selected = RiftItems.destination(gun);
        List<World> worlds = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (!settings.disabledWorlds().contains(world.getName()) && worlds.size() < MAX_WORLDS) {
                worlds.add(world);
            }
        }
        int worldRows = Math.max(1, (worlds.size() + WORLDS_PER_ROW - 1) / WORLDS_PER_ROW);
        int journeyRow = 9 * (1 + worldRows);
        int homeRow = journeyRow + 9;
        int footer = homeRow + 9;
        int size = footer + 9;

        Holder holder = new Holder(Page.MAIN, -1, slot, RiftItems.gunId(gun));
        Inventory inventory = create(holder, size, text(player, "rift.menu.title"));
        ItemStack frame = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack accent = pane(Material.LIME_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, i == 3 || i == 5 ? accent : frame);
            inventory.setItem(footer + i, frame);
        }

        // header: whether the gun is attuned, the gun itself (tank and destination in its tooltip), and the only free
        // slot of the menu with the load button next to it
        inventory.setItem(0, currentDestination(player, gun));
        inventory.setItem(2, compassStatus(player, gun));
        inventory.setItem(4, gun.clone());
        inventory.setItem(INPUT_SLOT, null);
        button(holder, inventory, LOAD_SLOT, loadButton(player, gun), () -> load(player, holder));

        // worlds: a random spot and the spawn of every world that is not disabled, side by side
        inventory.setItem(9, label(Material.FILLED_MAP, text(player, "rift.menu.worlds")));
        for (int index = 0; index < worlds.size(); index++) {
            World world = worlds.get(index);
            int randomSlot = 9 * (1 + index / WORLDS_PER_ROW) + 1 + (index % WORLDS_PER_ROW) * 2;
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
        }

        // journeys: back, last death and the rift atlas
        inventory.setItem(journeyRow, label(Material.CLOCK, text(player, "rift.menu.special")));
        Location back = module.storage().back(player.getUniqueId());
        destinationButton(
                player,
                holder,
                inventory,
                journeyRow + 1,
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
                journeyRow + 2,
                Material.SKELETON_SKULL,
                text(player, "rift.destination.death"),
                lines(player, death == null ? "rift.menu.death-empty" : "rift.menu.death-lore", coordinates(death)),
                RiftDestination.death(),
                selected);
        holder.atlasSlot = journeyRow + 4;
        button(holder, inventory, holder.atlasSlot, atlasButton(player, gun), () -> {
            ItemStack current = player.getInventory().getItem(holder.slot);
            if (RiftItems.isAttuned(current)) {
                openAtlas(player, holder.slot);
            } else {
                // a rift compass in the inventory unlocks the atlas right here
                insertCompass(player, holder);
            }
        });

        // homes: the number shows as the stack size; a set home opens its options, an empty one is set right away
        inventory.setItem(homeRow, label(Material.RED_BED, text(player, "rift.menu.homes")));
        for (int home = 0; home < settings.homes(); home++) {
            int number = home;
            Location location = module.storage().home(player.getUniqueId(), home);
            Map<String, String> placeholders = new HashMap<>(coordinates(location));
            placeholders.put("home", String.valueOf(home + 1));
            ItemStack icon;
            Runnable action;
            if (location == null) {
                icon = item(
                        Material.LIGHT_GRAY_DYE,
                        text(player, "rift.menu.home-empty", placeholders),
                        lines(player, "rift.menu.home-empty-lore", placeholders));
                action = () -> {
                    setHome(player, number);
                    choose(player, holder, RiftDestination.home(number));
                };
            } else {
                icon = item(
                        Material.LIME_BED,
                        text(player, "rift.menu.home", placeholders),
                        lines(player, "rift.menu.home-lore", placeholders));
                if (RiftDestination.home(home).equals(selected)) {
                    markSelected(player, icon);
                }
                action = () -> openHome(player, holder.slot, number);
            }
            icon.setAmount(home + 1);
            button(holder, inventory, homeRow + 1 + home, icon, action);
        }

        inventory.setItem(
                footer + 3,
                item(
                        Material.KNOWLEDGE_BOOK,
                        text(player, "rift.menu.help"),
                        lines(player, "rift.menu.guide", Map.of())));
        button(holder, inventory, footer + 5, closeBanner(text(player, "rift.menu.close")), player::closeInventory);
        player.openInventory(inventory);
    }

    /**
     * The rift atlas: every structure kind as a destination, unlocked while the gun is attuned. One row per dimension
     * (the overworld takes two), each led by its dimension's block.
     */
    private void openAtlas(Player player, int slot) {
        ItemStack gun = player.getInventory().getItem(slot);
        if (!RiftItems.isGun(gun) || !RiftItems.isAttuned(gun)) {
            open(player, slot);
            return;
        }
        Holder holder = new Holder(Page.ATLAS, -1, slot, RiftItems.gunId(gun));
        Inventory inventory = create(holder, 45, text(player, "rift.menu.atlas-title"));
        ItemStack frame = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, frame);
            inventory.setItem(36 + i, frame);
        }
        inventory.setItem(
                4,
                item(
                        Material.RECOVERY_COMPASS,
                        text(player, "rift.menu.compass-active", Map.of("time", RiftItems.remaining(gun))),
                        lines(player, "rift.menu.atlas-info", Map.of())));
        RiftDestination selected = RiftItems.destination(gun);
        int overworld = 0;
        int nether = 0;
        for (RiftStructure structure : RiftStructure.values()) {
            int structureSlot;
            switch (structure.environment()) {
                case NETHER -> structureSlot = 28 + nether++;
                case THE_END -> structureSlot = 33;
                default -> {
                    structureSlot = overworld < 7 ? 10 + overworld : 19 + overworld - 7;
                    overworld++;
                }
            }
            Map<String, String> placeholders = Map.of(
                    "structure", RiftNames.structure(structure.id(), player),
                    "dimension",
                            text(
                                    player,
                                    "rift.dimension."
                                            + structure.environment().name().toLowerCase()));
            RiftDestination destination = RiftDestination.structure(structure);
            destinationButton(
                    player,
                    holder,
                    inventory,
                    structureSlot,
                    structure.icon(),
                    text(player, "rift.menu.structure", placeholders),
                    lines(player, "rift.menu.structure-lore", placeholders),
                    destination,
                    selected);
        }
        inventory.setItem(9, label(Material.GRASS_BLOCK, text(player, "rift.dimension.normal")));
        inventory.setItem(27, label(Material.NETHERRACK, text(player, "rift.dimension.nether")));
        inventory.setItem(32, label(Material.END_STONE, text(player, "rift.dimension.the_end")));
        button(holder, inventory, 40, backBanner(text(player, "rift.menu.back")), () -> open(player, holder.slot));
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
        fill(inventory);
        button(
                holder,
                inventory,
                10,
                item(
                        Material.ENDER_PEARL,
                        text(player, "rift.menu.home-select"),
                        lines(player, "rift.menu.home-select-lore", placeholders)),
                () -> choose(player, holder, RiftDestination.home(home)));
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
        button(holder, inventory, 22, backBanner(text(player, "rift.menu.back")), () -> open(player, holder.slot));
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
        fill(inventory);
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
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        int size = event.getInventory().getSize();
        if (holder.page == Page.MAIN && mayMoveItems(event, raw, size)) {
            return; // putting things into the free slot, taking them out, or tidying up the own inventory
        }
        // nothing else moves in or out of the menu, whatever kind of click it is
        event.setCancelled(true);
        if (raw < 0 || raw >= size) {
            return;
        }
        ItemStack gun = player.getInventory().getItem(holder.slot);
        if (!RiftItems.isGun(gun) || !holder.gunId.equals(RiftItems.gunId(gun))) {
            player.closeInventory(); // the gun was moved or swapped in the meantime
            return;
        }
        Runnable action = holder.actions.get(raw);
        if (action != null) {
            player.playSound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
            action.run();
        }
    }

    /**
     * @return whether a click on the main page may move items: in the free slot only portal fluid and rift compasses
     *     go in (anything there may be taken out), in the own inventory everything works as usual
     */
    private static boolean mayMoveItems(InventoryClickEvent event, int raw, int size) {
        InventoryAction action = event.getAction();
        if (action == InventoryAction.COLLECT_TO_CURSOR || action == InventoryAction.UNKNOWN) {
            return false; // would pull matching items out of the menu
        }
        if (raw == INPUT_SLOT) {
            ItemStack incoming = action == InventoryAction.HOTBAR_SWAP
                    ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton())
                    : event.getCursor();
            return isEmpty(incoming) || isLoadable(incoming);
        }
        if (raw >= size) {
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                // shift click: it can only land in the free slot
                ItemStack moving = event.getCurrentItem();
                ItemStack waiting = event.getInventory().getItem(INPUT_SLOT);
                return isLoadable(moving) && (isEmpty(waiting) || waiting.isSimilar(moving));
            }
            return true;
        }
        return false;
    }

    private static boolean isLoadable(ItemStack item) {
        RiftItems.Type type = RiftItems.type(item);
        return type == RiftItems.Type.PORTAL_FLUID || type == RiftItems.Type.RIFT_COMPASS;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    @EventHandler
    public void on(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        int size = event.getInventory().getSize();
        boolean allowed = holder.page == Page.MAIN;
        for (int raw : event.getRawSlots()) {
            if (raw < size && (raw != INPUT_SLOT || !isLoadable(event.getOldCursor()))) {
                allowed = false;
            }
        }
        if (!allowed) {
            event.setCancelled(true);
        }
    }

    /** Whatever is left in the free slot goes back to the player when the menu closes. */
    @EventHandler
    public void on(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder holder
                && holder.page == Page.MAIN
                && event.getPlayer() instanceof Player player) {
            ItemStack left = event.getInventory().getItem(INPUT_SLOT);
            event.getInventory().setItem(INPUT_SLOT, null);
            give(player, left);
        }
    }

    /** Hands back what waits in the free slot of open menus when the plugin stops. */
    void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof Holder holder && holder.page == Page.MAIN) {
                give(player, top.getItem(INPUT_SLOT));
                top.setItem(INPUT_SLOT, null);
                player.closeInventory();
            }
        }
    }

    private static void give(Player player, ItemStack item) {
        if (isEmpty(item)) {
            return;
        }
        for (ItemStack leftover : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /**
     * Loads one item from the free slot: portal fluid fills the tank (one bottle alone turns into an empty bottle in
     * the slot, from a stack the empty bottle goes to the inventory), a rift compass attunes the gun. The menu stays
     * open, so the next one can follow right away.
     */
    private void load(Player player, Holder holder) {
        RiftSettings settings = module.settings();
        Inventory inventory = holder.inventory;
        ItemStack input = inventory.getItem(INPUT_SLOT);
        ItemStack gun = player.getInventory().getItem(holder.slot);
        RiftItems.Type type = RiftItems.type(input);
        if (type == RiftItems.Type.PORTAL_FLUID) {
            int charges = RiftItems.charges(gun);
            if (charges >= settings.tankCapacity()) {
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
                player.sendMessage(text(player, "rift.tank-full"));
                return;
            }
            RiftItems.setCharges(gun, Math.min(settings.tankCapacity(), charges + settings.riftsPerFluid()), settings);
            if (input.getAmount() == 1) {
                inventory.setItem(INPUT_SLOT, new ItemStack(Material.GLASS_BOTTLE));
            } else {
                input.setAmount(input.getAmount() - 1);
                inventory.setItem(INPUT_SLOT, input);
                give(player, new ItemStack(Material.GLASS_BOTTLE));
            }
            player.playSound(player, Sound.ITEM_BOTTLE_EMPTY, 1f, 0.9f);
            player.playSound(player, Sound.BLOCK_BREWING_STAND_BREW, 0.6f, 1.6f);
        } else if (type == RiftItems.Type.RIFT_COMPASS) {
            // a second compass adds to the remaining time
            long start = Math.max(System.currentTimeMillis(), RiftItems.attunedUntil(gun));
            RiftItems.attune(gun, start + settings.compassMillis(), settings);
            input.setAmount(input.getAmount() - 1);
            inventory.setItem(INPUT_SLOT, input.getAmount() > 0 ? input : null);
            player.playSound(player, Sound.ITEM_LODESTONE_COMPASS_LOCK, 1f, 1f);
            player.playSound(player, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.6f);
            player.sendMessage(text(player, "rift.attuned", Map.of("time", RiftItems.remaining(gun))));
        } else {
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
            player.sendMessage(text(player, "rift.menu.load-nothing"));
            return;
        }
        player.getInventory().setItem(holder.slot, gun);
        // refresh the header in place; reopening would hand back what is still waiting in the slot
        inventory.setItem(2, compassStatus(player, gun));
        inventory.setItem(4, gun.clone());
        inventory.setItem(LOAD_SLOT, loadButton(player, gun));
        if (type == RiftItems.Type.RIFT_COMPASS) {
            refreshAtlasButton(player, holder, gun);
        }
    }

    private void setHome(Player player, int home) {
        module.storage().setHome(player.getUniqueId(), home, player.getLocation());
        player.playSound(player, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.7f, 1.4f);
        player.sendMessage(text(player, "rift.home-set", Map.of("home", String.valueOf(home + 1))));
    }

    /**
     * Makes the destination the gun's target and closes the menu: the next attack opens the rift.
     */
    private void choose(Player player, Holder holder, RiftDestination destination) {
        ItemStack gun = player.getInventory().getItem(holder.slot);
        RiftItems.setDestination(gun, destination, module.settings());
        player.getInventory().setItem(holder.slot, gun);
        player.closeInventory();
        player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        String ready =
                text(player, "rift.destination-ready", Map.of("destination", RiftNames.describe(destination, player)));
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(TextComponent.fromLegacyText(ready)));
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
            markSelected(player, icon);
        } else {
            addLine(icon, text(player, "rift.menu.click-select"));
        }
        button(holder, inventory, slot, icon, () -> choose(player, holder, destination));
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

    /**
     * The selected destination is unmistakable: it glows, its name gets a green tick and the first line of its
     * tooltip says so.
     */
    private static void markSelected(Player player, ItemStack icon) {
        glow(icon);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName("§a§l✔ §r" + meta.getDisplayName());
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(0, text(player, "rift.menu.current"));
        meta.setLore(lore);
        icon.setItemMeta(meta);
    }

    /** The first slot of the menu always shows where the next rift goes. */
    private ItemStack currentDestination(Player player, ItemStack gun) {
        RiftDestination destination = RiftItems.destination(gun);
        if (destination == null) {
            return item(
                    Material.STRUCTURE_VOID,
                    text(player, "rift.menu.current-none"),
                    lines(player, "rift.menu.current-none-lore", Map.of()));
        }
        ItemStack icon = item(
                destinationIcon(destination),
                text(player, "rift.menu.current-name", Map.of("destination", RiftNames.describe(destination, player))),
                List.of());
        glow(icon);
        return icon;
    }

    private static Material destinationIcon(RiftDestination destination) {
        return switch (destination.type()) {
            case WORLD -> {
                World world = Bukkit.getWorld(destination.world());
                yield world == null ? Material.GRASS_BLOCK : worldIcon(world);
            }
            case SPAWN -> Material.LODESTONE;
            case HOME -> Material.LIME_BED;
            case BACK -> Material.RECOVERY_COMPASS;
            case DEATH -> Material.SKELETON_SKULL;
            case STRUCTURE -> {
                RiftStructure structure = RiftStructure.byId(destination.world());
                yield structure == null ? Material.FILLED_MAP : structure.icon();
            }
        };
    }

    private ItemStack compassStatus(Player player, ItemStack gun) {
        RiftSettings settings = module.settings();
        boolean attuned = RiftItems.isAttuned(gun);
        Map<String, String> placeholders =
                Map.of("minutes", String.valueOf(settings.compassMillis() / 60_000), "time", RiftItems.remaining(gun));
        ItemStack icon = RiftItems.create(RiftItems.Type.RIFT_COMPASS, 1, settings);
        decorate(
                icon,
                text(player, attuned ? "rift.menu.compass-active" : "rift.menu.compass-idle", placeholders),
                lines(player, attuned ? "rift.menu.compass-active-lore" : "rift.menu.compass-idle-lore", placeholders));
        if (attuned) {
            glow(icon);
        }
        return icon;
    }

    private ItemStack atlasButton(Player player, ItemStack gun) {
        boolean attuned = RiftItems.isAttuned(gun);
        RiftDestination selected = RiftItems.destination(gun);
        ItemStack icon = item(
                attuned ? Material.FILLED_MAP : Material.MAP,
                text(player, attuned ? "rift.menu.atlas.name" : "rift.menu.atlas-locked"),
                lines(player, attuned ? "rift.menu.atlas.lore" : "rift.menu.atlas-locked-lore", Map.of()));
        if (selected != null && selected.type() == RiftDestination.Type.STRUCTURE) {
            markSelected(player, icon);
            addLine(
                    icon,
                    text(
                            player,
                            "rift.menu.selected-structure",
                            Map.of("structure", RiftNames.describe(selected, player))));
        }
        return icon;
    }

    private void refreshAtlasButton(Player player, Holder holder, ItemStack gun) {
        if (holder.atlasSlot >= 0) {
            holder.inventory.setItem(holder.atlasSlot, atlasButton(player, gun));
        }
    }

    private ItemStack loadButton(Player player, ItemStack gun) {
        RiftSettings settings = module.settings();
        // a lime arrow pouring down into a green glow
        ItemStack banner = new BannerBuilder()
                .setBaseColor(DyeColor.BLACK)
                .setPatterns(
                        new Pattern(DyeColor.GREEN, PatternType.GRADIENT_UP),
                        new Pattern(DyeColor.LIME, PatternType.STRIPE_BOTTOM),
                        new Pattern(DyeColor.LIME, PatternType.TRIANGLE_BOTTOM),
                        new Pattern(DyeColor.LIME, PatternType.STRIPE_CENTER),
                        new Pattern(DyeColor.BLACK, PatternType.STRIPE_TOP),
                        new Pattern(DyeColor.BLACK, PatternType.CURLY_BORDER))
                .hideItemFlags()
                .build();
        decorate(
                banner,
                text(player, "rift.menu.load.name"),
                lines(
                        player,
                        "rift.menu.load.lore",
                        Map.of(
                                "rifts", String.valueOf(settings.riftsPerFluid()),
                                "charges", String.valueOf(RiftItems.charges(gun)),
                                "capacity", String.valueOf(settings.tankCapacity()))));
        return banner;
    }

    private static ItemStack closeBanner(String name) {
        return new BannerBuilder()
                .setBaseColor(DyeColor.LIGHT_GRAY)
                .setName(name)
                .setPatterns(
                        new Pattern(DyeColor.RED, PatternType.CROSS),
                        new Pattern(DyeColor.LIGHT_GRAY, PatternType.BORDER))
                .hideItemFlags()
                .build();
    }

    /** A banner with an arrow pointing left. */
    private static ItemStack backBanner(String name) {
        return new BannerBuilder()
                .setBaseColor(DyeColor.GRAY)
                .setName(name)
                .setPatterns(
                        new Pattern(DyeColor.WHITE, PatternType.RHOMBUS),
                        new Pattern(DyeColor.GRAY, PatternType.HALF_VERTICAL_RIGHT),
                        new Pattern(DyeColor.WHITE, PatternType.STRIPE_MIDDLE),
                        new Pattern(DyeColor.GRAY, PatternType.BORDER))
                .hideItemFlags()
                .build();
    }

    private static void fill(Inventory inventory) {
        ItemStack frame = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, frame);
        }
    }

    /** A frame pane without a tooltip. */
    private static ItemStack pane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setHideTooltip(true);
        pane.setItemMeta(meta);
        return pane;
    }

    /** A row label: an icon with a name (and optionally lines) that does nothing when clicked. */
    private static ItemStack label(Material material, String name) {
        return item(material, name, List.of());
    }

    private static void addLine(ItemStack item, String line) {
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(line);
        meta.setLore(lore);
        item.setItemMeta(meta);
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
