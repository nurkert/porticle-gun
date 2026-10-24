package eu.nurkert.porticlegun.commands;

import eu.nurkert.porticlegun.builders.BannerBuilder;
import eu.nurkert.porticlegun.builders.ItemBuilder;
import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.persistence.PersistentHandler;
import eu.nurkert.porticlegun.portals.ActivePortalsHandler;
import eu.nurkert.porticlegun.portals.Portal;
import eu.nurkert.porticlegun.visualization.GunColorHandler;
import eu.nurkert.porticlegun.visualization.GunColors;
import eu.nurkert.porticlegun.visualization.PortalColor;
import eu.nurkert.porticlegun.visualization.PortalGeometry;
import eu.nurkert.porticlegun.visualization.concrete.PortalVisualizationType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

/**
 * Portal management for admins, from the /porticlegun menu: every gun with open portals, listed by gun, by world or
 * by distance, and a page per gun to travel to its portals, block or open them and close them. Chest menus, so it
 * works the same on Java and Bedrock.
 */
public class AdminMenu implements Listener {

    private static final int SIZE = 54;
    private static final int FIRST_ENTRY = 9;
    private static final int ENTRIES = 36;
    private static final int TAB_GUNS = 2;
    private static final int TAB_WORLDS = 4;
    private static final int TAB_NEARBY = 6;
    private static final int SUMMARY = 8;
    private static final int BACK = 45;
    private static final int PREVIOUS = 48;
    private static final int PAGE = 49;
    private static final int NEXT = 50;
    // closing portals needs a second click within this time
    private static final long CONFIRM_MS = 3000;

    private enum Tab {
        GUNS,
        WORLDS,
        NEARBY
    }

    private final ActivePortalsHandler activePortals;
    private final PersistentHandler persistence;
    private final GunColorHandler gunColors;
    private final Consumer<Player> overview;
    private final Map<UUID, Long> confirmClose = new HashMap<>();

    public AdminMenu(
            ActivePortalsHandler activePortals,
            PersistentHandler persistence,
            GunColorHandler gunColors,
            Consumer<Player> overview) {
        this.activePortals = activePortals;
        this.persistence = persistence;
        this.gunColors = gunColors;
        this.overview = overview;
    }

    private static final class Holder implements InventoryHolder {
        private final Map<Integer, Runnable> buttons = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** A gun with at least one open portal. */
    private record Gun(String id, Portal primary, Portal secondary) {

        List<Portal> portals() {
            List<Portal> portals = new ArrayList<>(2);
            if (primary != null) {
                portals.add(primary);
            }
            if (secondary != null) {
                portals.add(secondary);
            }
            return portals;
        }
    }

    public void open(Player player) {
        showGuns(player, Tab.GUNS, null, 0);
    }

    private List<Gun> guns() {
        Map<String, Gun> guns = new LinkedHashMap<>();
        for (Portal portal : activePortals.getAllPortal()) {
            if (portal.getLocation() == null || portal.getLocation().getWorld() == null) {
                continue;
            }
            String id = portal.getGunID();
            guns.computeIfAbsent(
                    id,
                    key -> new Gun(key, activePortals.getPrimaryPortal(key), activePortals.getSecondaryPortal(key)));
        }
        return new ArrayList<>(guns.values());
    }

    /** The list of guns: all of them by user, those in one world, or those near the player by distance. */
    private void showGuns(Player player, Tab tab, String world, int page) {
        List<Gun> guns = guns();
        if (world != null) {
            guns.removeIf(gun -> gun.portals().stream()
                    .noneMatch(
                            portal -> portal.getLocation().getWorld().getName().equals(world)));
        }
        if (tab == Tab.NEARBY) {
            guns.removeIf(gun -> Double.isNaN(distance(player, gun)));
            guns.sort(Comparator.comparingDouble(gun -> distance(player, gun)));
        } else {
            guns.sort(Comparator.comparing((Gun gun) -> user(gun.id()).toLowerCase(Locale.ROOT))
                    .thenComparing(Gun::id));
        }
        String title = world == null
                ? MessageManager.getMessage(player, "menus.admin.title")
                : MessageManager.getMessage(player, "menus.admin.title-world", Map.of("world", world));
        Holder holder = frame(player, title, tab);

        int pages = Math.max(1, (guns.size() + ENTRIES - 1) / ENTRIES);
        int shown = Math.min(page, pages - 1);
        for (int i = 0; i < ENTRIES && shown * ENTRIES + i < guns.size(); i++) {
            Gun gun = guns.get(shown * ENTRIES + i);
            ItemStack item = gunItem(player, gun, tab == Tab.NEARBY ? distance(player, gun) : Double.NaN);
            holder.inventory.setItem(FIRST_ENTRY + i, item);
            holder.buttons.put(
                    FIRST_ENTRY + i, () -> showGun(player, gun.id(), () -> showGuns(player, tab, world, shown)));
        }
        if (guns.isEmpty()) {
            holder.inventory.setItem(
                    22,
                    new ItemBuilder(Material.STRUCTURE_VOID)
                            .setName(MessageManager.getMessage(player, "menus.admin.empty"))
                            .build());
        }
        paging(player, holder, shown, pages, next -> showGuns(player, tab, world, next));
        back(player, holder, world == null ? () -> overview.accept(player) : () -> showWorlds(player, 0));
        player.openInventory(holder.inventory);
    }

    /** One entry per world with open portals; a click lists the guns there. */
    private void showWorlds(Player player, int page) {
        Map<String, List<Portal>> worlds = new TreeMap<>();
        for (Portal portal : activePortals.getAllPortal()) {
            World world =
                    portal.getLocation() == null ? null : portal.getLocation().getWorld();
            if (world != null) {
                worlds.computeIfAbsent(world.getName(), key -> new ArrayList<>())
                        .add(portal);
            }
        }
        Holder holder = frame(player, MessageManager.getMessage(player, "menus.admin.title"), Tab.WORLDS);
        List<String> names = new ArrayList<>(worlds.keySet());
        int pages = Math.max(1, (names.size() + ENTRIES - 1) / ENTRIES);
        int shown = Math.min(page, pages - 1);
        for (int i = 0; i < ENTRIES && shown * ENTRIES + i < names.size(); i++) {
            String name = names.get(shown * ENTRIES + i);
            List<Portal> portals = worlds.get(name);
            World world = Bukkit.getWorld(name);
            Material icon = world == null
                    ? Material.GRASS_BLOCK
                    : switch (world.getEnvironment()) {
                        case NETHER -> Material.NETHERRACK;
                        case THE_END -> Material.END_STONE;
                        default -> Material.GRASS_BLOCK;
                    };
            long gunCount = portals.stream().map(Portal::getGunID).distinct().count();
            holder.inventory.setItem(
                    FIRST_ENTRY + i,
                    new ItemBuilder(icon)
                            .setName("§f" + name)
                            .setLore(
                                    MessageManager.getMessage(
                                            player,
                                            "menus.admin.world-lore",
                                            Map.of(
                                                    "portals", String.valueOf(portals.size()),
                                                    "guns", String.valueOf(gunCount))),
                                    MessageManager.getMessage(player, "menus.admin.open-hint"))
                            .build());
            holder.buttons.put(FIRST_ENTRY + i, () -> showGuns(player, Tab.WORLDS, name, 0));
        }
        if (names.isEmpty()) {
            holder.inventory.setItem(
                    22,
                    new ItemBuilder(Material.STRUCTURE_VOID)
                            .setName(MessageManager.getMessage(player, "menus.admin.empty"))
                            .build());
        }
        paging(player, holder, shown, pages, next -> showWorlds(player, next));
        back(player, holder, () -> overview.accept(player));
        player.openInventory(holder.inventory);
    }

    /** One gun: its portals (a click travels there), block or open them, close them. */
    private void showGun(Player player, String gunID, Runnable back) {
        Portal primary = activePortals.getPrimaryPortal(gunID);
        Portal secondary = activePortals.getSecondaryPortal(gunID);
        Holder holder = new Holder();
        holder.inventory = Bukkit.createInventory(
                holder, SIZE, MessageManager.getMessage(player, "menus.admin.title-gun", Map.of("user", user(gunID))));
        GunColors colors = gunColors.getColors(gunID);
        Gun gun = new Gun(gunID, primary, secondary);
        holder.inventory.setItem(4, gunItem(player, gun, Double.NaN));

        portalButton(player, holder, 20, primary, colors.getPrimary(), "menus.admin.primary");
        portalButton(player, holder, 24, secondary, colors.getSecondary(), "menus.admin.secondary");

        boolean blocked = activePortals.isBlocked(gunID);
        String state = MessageManager.getMessage(
                player, blocked ? "menus.settings.connection-off" : "menus.settings.connection-on");
        holder.inventory.setItem(
                30,
                new BannerBuilder()
                        .setBaseColor(DyeColor.WHITE)
                        .setPatterns(
                                blocked
                                        ? new Pattern(DyeColor.RED, PatternType.STRIPE_MIDDLE)
                                        : new Pattern(DyeColor.GREEN, PatternType.STRIPE_CENTER),
                                new Pattern(DyeColor.LIGHT_GRAY, PatternType.BORDER))
                        .hideItemFlags()
                        .setName(MessageManager.getMessage(
                                player, "menus.settings.connection-label", Map.of("state", state)))
                        .setLore(MessageManager.getMessage(player, "menus.admin.toggle-hint"))
                        .build());
        holder.buttons.put(30, () -> {
            activePortals.setBlocked(gunID, !blocked);
            showGun(player, gunID, back);
        });

        boolean confirming = isConfirming(player);
        holder.inventory.setItem(
                32,
                new ItemBuilder(Material.TNT)
                        .setName(MessageManager.getMessage(
                                player, confirming ? "menus.admin.close-confirm" : "menus.admin.close"))
                        .setLore(MessageManager.getMessage(player, "menus.admin.close-hint"))
                        .build());
        holder.buttons.put(32, () -> {
            if (isConfirming(player)) {
                confirmClose.remove(player.getUniqueId());
                activePortals.closePortals(gunID);
                player.playSound(player, Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.2f);
                back.run();
            } else {
                confirmClose.put(player.getUniqueId(), System.currentTimeMillis());
                showGun(player, gunID, back);
            }
        });

        fillRow(player, holder, 0);
        fillRow(player, holder, 5);
        back(player, holder, back);
        player.openInventory(holder.inventory);
    }

    private boolean isConfirming(Player player) {
        Long since = confirmClose.get(player.getUniqueId());
        return since != null && System.currentTimeMillis() - since < CONFIRM_MS;
    }

    private void portalButton(Player player, Holder holder, int slot, Portal portal, PortalColor color, String key) {
        String name = MessageManager.getMessage(player, key);
        if (portal == null) {
            holder.inventory.setItem(
                    slot,
                    new ItemBuilder(Material.BARRIER)
                            .setName("§f" + name)
                            .setLore(MessageManager.getMessage(player, "menus.admin.not-placed"))
                            .build());
            return;
        }
        Location location = portal.getLocation();
        String facing = MessageManager.getMessage(
                player,
                portal.getDirection().getY() > 0
                        ? "menus.admin.facing-floor"
                        : portal.getDirection().getY() < 0 ? "menus.admin.facing-ceiling" : "menus.admin.facing-wall");
        String shape = MessageManager.getMessage(
                player,
                portal.getVisualizationType() == PortalVisualizationType.ELLIPTIC
                        ? "menus.admin.shape-round"
                        : "menus.admin.shape-square");
        holder.inventory.setItem(
                slot,
                new BannerBuilder()
                        .setBaseColor(color.getDyeColor())
                        .hideItemFlags()
                        .setName(color.getChatColor() + name)
                        .setLore(
                                "§f" + position(location),
                                "§f" + facing + ", " + shape,
                                "",
                                MessageManager.getMessage(player, "menus.admin.teleport-hint"))
                        .build());
        holder.buttons.put(slot, () -> {
            player.closeInventory();
            player.teleport(standingSpot(portal));
            player.playSound(player, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f);
        });
    }

    /**
     * A spot to stand next to a portal, facing it: in front of a wall portal, beside a floor or ceiling portal; never
     * inside it, or the player would go straight through.
     */
    static Location standingSpot(Portal portal) {
        Location base = portal.getLocation().clone().add(0.5, 0, 0.5);
        Vector direction = portal.getDirection();
        List<Vector> offsets = direction.getY() == 0.0
                ? List.of(direction.clone().multiply(1.5), direction.clone().multiply(2.5))
                : List.of(new Vector(1.5, 0, 0), new Vector(-1.5, 0, 0), new Vector(0, 0, 1.5), new Vector(0, 0, -1.5));
        Location centre = PortalGeometry.computePortalCenter(portal);
        for (Vector offset : offsets) {
            for (int down = 0; down <= 1; down++) {
                Location spot = base.clone().add(offset).subtract(0, down, 0);
                if (spot.getBlock().isPassable()
                        && spot.clone().add(0, 1, 0).getBlock().isPassable()
                        && !spot.clone().subtract(0, 1, 0).getBlock().isPassable()) {
                    Vector look = centre.toVector()
                            .subtract(spot.clone().add(0, 1.62, 0).toVector());
                    if (look.lengthSquared() > 0) {
                        spot.setDirection(look);
                    }
                    return spot;
                }
            }
        }
        return base.add(direction.getY() == 0.0 ? direction.clone().multiply(1.5) : new Vector(1.5, 0, 0));
    }

    private ItemStack gunItem(Player player, Gun gun, double distance) {
        GunColors colors = gunColors.getColors(gun.id());
        List<String> lore = new ArrayList<>();
        lore.add(portalLine(player, "menus.admin.primary", gun.primary()));
        lore.add(portalLine(player, "menus.admin.secondary", gun.secondary()));
        String status;
        if (gun.primary() == null || gun.secondary() == null) {
            status = "menus.admin.status-single";
        } else {
            status = activePortals.isBlocked(gun.id()) ? "menus.admin.status-blocked" : "menus.admin.status-open";
        }
        lore.add(MessageManager.getMessage(player, status));
        if (!Double.isNaN(distance)) {
            lore.add(MessageManager.getMessage(
                    player, "menus.admin.distance", Map.of("distance", String.valueOf(Math.round(distance)))));
        }
        lore.add("§f" + gun.id());
        lore.add("");
        lore.add(MessageManager.getMessage(player, "menus.admin.open-hint"));
        ItemStack item = new BannerBuilder()
                .setBaseColor(colors.getPrimary().getDyeColor())
                .setPatterns(new Pattern(colors.getSecondary().getDyeColor(), PatternType.HALF_HORIZONTAL_BOTTOM))
                .hideItemFlags()
                .setName(MessageManager.getMessage(player, "menus.admin.gun-name", Map.of("user", user(gun.id()))))
                .build();
        ItemMeta meta = item.getItemMeta();
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String portalLine(Player player, String key, Portal portal) {
        String where = portal == null
                ? MessageManager.getMessage(player, "menus.admin.not-placed")
                : "§e" + position(portal.getLocation());
        return "§f" + MessageManager.getMessage(player, key) + ": " + where;
    }

    private static String position(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " "
                + location.getBlockZ();
    }

    /** @return the distance to the nearest portal of the gun in the player's world, NaN if none is there */
    private static double distance(Player player, Gun gun) {
        double nearest = Double.NaN;
        for (Portal portal : gun.portals()) {
            if (portal.getLocation().getWorld().equals(player.getWorld())) {
                double distance = portal.getLocation().distance(player.getLocation());
                if (Double.isNaN(nearest) || distance < nearest) {
                    nearest = distance;
                }
            }
        }
        return nearest;
    }

    /** @return who last opened a portal with the gun, or a short form of its id for portals from before 1.6.14 */
    private String user(String gunID) {
        String user = persistence.get("porticleguns." + ItemHandler.saveable(gunID) + ".user");
        return user != null ? user : gunID.substring(0, Math.min(8, gunID.length()));
    }

    /** The menu's frame: tabs on top, the navigation row below. */
    private Holder frame(Player player, String title, Tab selected) {
        Holder holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, SIZE, title);
        tab(
                player,
                holder,
                TAB_GUNS,
                Material.ENDER_EYE,
                "menus.admin.tab-guns",
                selected == Tab.GUNS,
                () -> open(player));
        tab(
                player,
                holder,
                TAB_WORLDS,
                Material.GRASS_BLOCK,
                "menus.admin.tab-worlds",
                selected == Tab.WORLDS,
                () -> showWorlds(player, 0));
        tab(
                player,
                holder,
                TAB_NEARBY,
                Material.COMPASS,
                "menus.admin.tab-nearby",
                selected == Tab.NEARBY,
                () -> showGuns(player, Tab.NEARBY, null, 0));

        List<Portal> portals = activePortals.getAllPortal();
        long guns = portals.stream().map(Portal::getGunID).distinct().count();
        long blocked = portals.stream()
                .map(Portal::getGunID)
                .distinct()
                .filter(activePortals::isBlocked)
                .count();
        holder.inventory.setItem(
                SUMMARY,
                new ItemBuilder(Material.BOOK)
                        .setName(MessageManager.getMessage(player, "menus.admin.summary"))
                        .setLore(MessageManager.getMessage(
                                player,
                                "menus.admin.summary-lore",
                                Map.of(
                                        "portals", String.valueOf(portals.size()),
                                        "guns", String.valueOf(guns),
                                        "blocked", String.valueOf(blocked))))
                        .build());
        fillRow(player, holder, 0);
        fillRow(player, holder, 5);
        return holder;
    }

    private static void tab(
            Player player, Holder holder, int slot, Material icon, String key, boolean selected, Runnable action) {
        ItemBuilder builder =
                new ItemBuilder(icon).setName((selected ? "§a§l" : "§f") + MessageManager.getMessage(player, key));
        if (selected) {
            builder.addGlow();
        }
        ItemStack item = builder.build();
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        holder.inventory.setItem(slot, item);
        holder.buttons.put(slot, action);
    }

    private static void paging(Player player, Holder holder, int page, int pages, Consumer<Integer> show) {
        if (page > 0) {
            holder.inventory.setItem(
                    PREVIOUS,
                    new ItemBuilder(Material.ARROW)
                            .setName(MessageManager.getMessage(player, "menus.admin.previous"))
                            .build());
            holder.buttons.put(PREVIOUS, () -> show.accept(page - 1));
        }
        if (page < pages - 1) {
            holder.inventory.setItem(
                    NEXT,
                    new ItemBuilder(Material.ARROW)
                            .setName(MessageManager.getMessage(player, "menus.admin.next"))
                            .build());
            holder.buttons.put(NEXT, () -> show.accept(page + 1));
        }
        holder.inventory.setItem(
                PAGE,
                new ItemBuilder(Material.PAPER)
                        .setName(MessageManager.getMessage(
                                player,
                                "menus.admin.page",
                                Map.of("page", String.valueOf(page + 1), "pages", String.valueOf(pages))))
                        .build());
    }

    private static void back(Player player, Holder holder, Runnable action) {
        holder.inventory.setItem(
                BACK,
                new ItemBuilder(Material.ARROW)
                        .setName(MessageManager.getMessage(player, "menus.porticlegun.back"))
                        .build());
        holder.buttons.put(BACK, action);
    }

    private static void fillRow(Player player, Holder holder, int row) {
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setName(MessageManager.getMessage(player, "menus.porticlegun.filler-name"))
                .build();
        for (int slot = row * 9; slot < row * 9 + 9; slot++) {
            if (holder.inventory.getItem(slot) == null) {
                holder.inventory.setItem(slot, filler);
            }
        }
    }

    @EventHandler
    public void on(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // a pure menu: nothing moves, in or out
        event.setCancelled(true);
        if (!player.hasPermission(Permissions.ADMIN)) {
            player.closeInventory();
            return;
        }
        Runnable button = holder.buttons.get(event.getRawSlot());
        if (button != null) {
            player.playSound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
            button.run();
        }
    }

    @EventHandler
    public void on(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
