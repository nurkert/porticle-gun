package eu.nurkert.porticlegun.portals;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.audio.AudioHandler;
import eu.nurkert.porticlegun.builders.BannerBuilder;
import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.persistence.PersistentHandler;
import eu.nurkert.porticlegun.util.BedrockSupport;
import eu.nurkert.porticlegun.visualization.GunColorHandler;
import eu.nurkert.porticlegun.visualization.GunColors;
import eu.nurkert.porticlegun.visualization.PortalColor;
import eu.nurkert.porticlegun.visualization.concrete.PortalVisualizationType;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * The settings menu of a gun (sneak and right click): portal colours, portal shape and closing the portals.
 */
public class SettingsHandler implements Listener {

    private final ActivePortalsHandler activePortals;
    private final GunColorHandler gunColors;
    private final PersistentHandler persistence;
    private final Predicate<Player> resourcePack;

    public SettingsHandler(
            ActivePortalsHandler activePortals,
            GunColorHandler gunColors,
            PersistentHandler persistence,
            Predicate<Player> resourcePack) {
        this.activePortals = activePortals;
        this.gunColors = gunColors;
        this.persistence = persistence;
        this.resourcePack = resourcePack;
    }

    final ItemStack resetTemplate = new BannerBuilder()
            .setBaseColor(DyeColor.LIGHT_GRAY)
            .setPatterns(
                    new Pattern(DyeColor.RED, PatternType.CROSS), new Pattern(DyeColor.LIGHT_GRAY, PatternType.BORDER))
            .hideItemFlags()
            .build();

    HashMap<String, Long> lastClick = new HashMap<>();

    /** Marks a settings menu and the gun it belongs to, so it is recognised whatever its title looks like. */
    private static final class Holder implements InventoryHolder {
        private final String gunID;
        private Inventory inventory;

        private Holder(String gunID) {
            this.gunID = gunID;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String gunID = isPorticleSettingsInv(event.getView());
        if (gunID == null) {
            return;
        }
        // nothing moves into or out of the menu; the player's own inventory works as usual, but nothing can be
        // shift-clicked or double-click collected from there into the menu
        int clicked = event.getRawSlot();
        boolean menu =
                clicked >= 0 && clicked < event.getView().getTopInventory().getSize();
        if (menu || event.isShiftClick() || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
        }
        if (!menu || event.getCurrentItem() == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (clicked == 1) {
            boolean blocked = !activePortals.isBlocked(gunID);
            activePortals.setBlocked(gunID, blocked);
            event.getView().getTopInventory().setItem(1, flat(createConnectionItem(blocked, player), player));
            AudioHandler.playSound(
                    player, blocked ? AudioHandler.PortalSound.PORTAL_CLOSE : AudioHandler.PortalSound.CLICK);
            return;
        } else if (clicked == 4) {
            if (lastClick.containsKey(gunID) && System.currentTimeMillis() - lastClick.get(gunID) < 500) {
                activePortals.closePortals(gunID);
                AudioHandler.playSound(player, AudioHandler.PortalSound.PORTAL_CLOSE);
            } else {
                lastClick.put(gunID, System.currentTimeMillis());
            }
        } else if (clicked == 0 || clicked == 6) {
            PortalColor before = gunColors.getColors(gunID).getPrimary();
            if (clicked == 0) {
                gunColors.selectNextPrimary(gunID);
            } else {
                gunColors.selectPreviousPrimary(gunID);
            }
            changeColour(event.getView().getTopInventory(), gunID, player, 3, before, clicked == 6);
        } else if (clicked == 2 || clicked == 8) {
            PortalColor before = gunColors.getColors(gunID).getSecondary();
            if (clicked == 2) {
                gunColors.selectNextSecondary(gunID);
            } else {
                gunColors.selectPreviousSecondary(gunID);
            }
            changeColour(event.getView().getTopInventory(), gunID, player, 5, before, clicked == 8);
        } else if (clicked == 7) {
            for (Portal portal :
                    new Portal[] {activePortals.getPrimaryPortal(gunID), activePortals.getSecondaryPortal(gunID)}) {
                if (portal != null) {
                    portal.toggleVisualizationType();
                }
            }
            PortalVisualizationType visualizationType = PortalVisualizationType.fromString(
                    persistence.get("porticleguns." + ItemHandler.saveable(gunID) + ".shape"));
            persistence.set(
                    "porticleguns." + ItemHandler.saveable(gunID) + ".shape",
                    visualizationType.getNext().toString());
            event.getView()
                    .getTopInventory()
                    .setContents(getCurrentSettings(gunID, player).getContents());
        } else {
            return;
        }
        AudioHandler.playSound(player, AudioHandler.PortalSound.CLICK);
    }

    /** The colour banner: filled with the colour, so it is clear at a glance which colour a portal has. */
    public ItemStack getColorPreview(PortalColor color, Player player) {
        return new BannerBuilder()
                .setBaseColor(color.getDyeColor())
                .setName(color.getChatColor() + color.displayName(player))
                .hideItemFlags()
                .build();
    }

    /**
     * An arrow in the colour it switches to, so the next and the previous colour can be seen before clicking: a sharp
     * triangle, pointing up in the upper half of the banner (above the colour banner in the menu) or down in the
     * lower half (below it). The background stays black, grey only behind a black arrow so it can be seen.
     *
     * @param up an arrow pointing up (next colour) or down (previous colour)
     */
    private static ItemStack arrow(PortalColor target, boolean up, Player player) {
        DyeColor dye = target.getDyeColor();
        DyeColor background = arrowBackground(target);
        return arrowBanner(
                target,
                up,
                player,
                // a diamond with its lower (or upper) half covered leaves a triangle with clean, sharp edges
                new Pattern(dye, PatternType.RHOMBUS),
                new Pattern(background, up ? PatternType.HALF_HORIZONTAL_BOTTOM : PatternType.HALF_HORIZONTAL));
    }

    /**
     * The arrow on its way in: a larger triangle at the far edge, so the arrow seems to slide in the direction it
     * points to before it settles.
     */
    private static ItemStack arrowSlidingIn(PortalColor target, boolean up, Player player) {
        return arrowBanner(
                target,
                up,
                player,
                new Pattern(target.getDyeColor(), up ? PatternType.TRIANGLE_BOTTOM : PatternType.TRIANGLE_TOP));
    }

    private static ItemStack arrowBanner(PortalColor target, boolean up, Player player, Pattern... patterns) {
        return new BannerBuilder()
                .setBaseColor(arrowBackground(target))
                .setName((up ? "§f▲ " : "§f▼ ") + target.getChatColor() + target.displayName(player))
                .setPatterns(patterns)
                .hideItemFlags()
                .build();
    }

    private static DyeColor arrowBackground(PortalColor target) {
        return target.getDyeColor() == DyeColor.BLACK ? DyeColor.GRAY : DyeColor.BLACK;
    }

    // running colour animations, by menu and colour banner slot, so a fast second click restarts instead of overlapping
    private final Map<Inventory, Map<Integer, BukkitTask>> animations = new HashMap<>();

    /**
     * Shows a new colour in four frames, two ticks apart: the colour banner fills with the new colour, from below
     * after the down arrow and from above after the up arrow, while the two arrows slide in with their new colours.
     */
    private void changeColour(
            Inventory inventory, String gunID, Player player, int slot, PortalColor before, boolean fromBelow) {
        GunColors colors = gunColors.getColors(gunID);
        PortalColor after = slot == 3 ? colors.getPrimary() : colors.getSecondary();
        int column = slot == 3 ? 0 : 2;

        Map<Integer, BukkitTask> running = animations.computeIfAbsent(inventory, key -> new HashMap<>());
        BukkitTask previous = running.remove(slot);
        if (previous != null) {
            previous.cancel();
        }
        DyeColor oldDye = before.getDyeColor();
        DyeColor newDye = after.getDyeColor();
        String name = "§r" + after.getChatColor() + after.displayName(player);
        PatternType edge = fromBelow ? PatternType.STRIPE_BOTTOM : PatternType.STRIPE_TOP;
        PatternType half = fromBelow ? PatternType.HALF_HORIZONTAL_BOTTOM : PatternType.HALF_HORIZONTAL;
        PatternType rest = fromBelow ? PatternType.STRIPE_TOP : PatternType.STRIPE_BOTTOM;
        ItemStack[] banners = {
            flat(fill(oldDye, new Pattern(newDye, edge), name), player),
            flat(fill(oldDye, new Pattern(newDye, half), name), player),
            flat(fill(newDye, new Pattern(oldDye, rest), name), player),
            flat(getColorPreview(after, player), player)
        };
        PortalColor next = after.next();
        PortalColor previousColour = after.previous();
        ItemStack[] upArrows = {
            flat(arrowSlidingIn(next, true, player), player), flat(arrow(next, true, player), player)
        };
        ItemStack[] downArrows = {
            flat(arrowSlidingIn(previousColour, false, player), player),
            flat(arrow(previousColour, false, player), player)
        };
        running.put(
                slot,
                new BukkitRunnable() {
                    private int frame;

                    @Override
                    public void run() {
                        if (inventory.getViewers().isEmpty() || frame >= banners.length) {
                            cancel();
                            Map<Integer, BukkitTask> tasks = animations.get(inventory);
                            if (tasks != null) {
                                tasks.remove(slot);
                                if (tasks.isEmpty()) {
                                    animations.remove(inventory);
                                }
                            }
                            return;
                        }
                        inventory.setItem(slot, banners[frame]);
                        if (frame < upArrows.length) {
                            inventory.setItem(column, upArrows[frame]);
                            inventory.setItem(column + 6, downArrows[frame]);
                        }
                        frame++;
                    }
                }.runTaskTimer(PorticleGun.getInstance(), 0L, 2L));
    }

    /**
     * For Java players with the resource pack the menu's banners show flat and from the front, without pole
     * ({@code porticlegun:gui_banner_<colour>}, a wall banner filling the slot); everyone else sees vanilla banners.
     */
    private ItemStack flat(ItemStack banner, Player player) {
        String material = banner.getType().name();
        if (!material.endsWith("_BANNER") || !drawn(player)) {
            return banner;
        }
        String colour =
                material.substring(0, material.length() - "_BANNER".length()).toLowerCase(Locale.ROOT);
        ItemMeta meta = banner.getItemMeta();
        meta.setItemModel(new NamespacedKey(PorticleGun.getInstance(), "gui_banner_" + colour));
        banner.setItemMeta(meta);
        return banner;
    }

    /** Java players with the resource pack get the drawn menu (SettingsGui) and flat banners. */
    private boolean drawn(Player player) {
        return !BedrockSupport.isBedrock(player) && resourcePack.test(player);
    }

    private static ItemStack fill(DyeColor base, Pattern pattern, String name) {
        return new BannerBuilder()
                .setBaseColor(base)
                .setName(name)
                .setPatterns(pattern)
                .hideItemFlags()
                .build();
    }

    @EventHandler
    public void on(InventoryDragEvent event) {
        if (isPorticleSettingsInv(event.getView()) == null) {
            return;
        }
        int top = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < top) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * The connection switch: green passage while things can go through the portals, red bar while they are blocked
     * (they stay where they are, but push everything back).
     */
    private static ItemStack createConnectionItem(boolean blocked, Player player) {
        String state = MessageManager.getMessage(
                player, blocked ? "menus.settings.connection-off" : "menus.settings.connection-on");
        ItemStack item = new BannerBuilder()
                .setBaseColor(DyeColor.WHITE)
                .setPatterns(
                        blocked
                                ? new Pattern(DyeColor.RED, PatternType.STRIPE_MIDDLE)
                                : new Pattern(DyeColor.GREEN, PatternType.STRIPE_CENTER),
                        new Pattern(DyeColor.LIGHT_GRAY, PatternType.BORDER))
                .hideItemFlags()
                .setName(MessageManager.getMessage(player, "menus.settings.connection-label", Map.of("state", state)))
                .build();
        ItemMeta meta = item.getItemMeta();
        meta.setLore(List.of(MessageManager.getMessage(
                player, blocked ? "menus.settings.connection-lore-off" : "menus.settings.connection-lore-on")));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void on(PlayerInteractEvent event) {
        // check if player is holding a gun and is sneaking
        if (event.getItem() != null && event.getPlayer().isSneaking()) {
            // check if player is holding a gun
            String gunID = ItemHandler.isValidGun(event.getItem());
            if (gunID != null) {
                Player player = event.getPlayer();

                AudioHandler.playSound(player, AudioHandler.PortalSound.INV_OPEN);
                Inventory inv = getCurrentSettings(gunID, player);
                player.openInventory(inv);
            }
        }
    }

    /**
     * Get an inventory with the current settings of the gun
     * @param gunID the id of the gun
     * @return
     */
    public Inventory getCurrentSettings(String gunID, Player player) {
        String title = MessageManager.getMessage(player, "menus.settings.title");
        Holder holder = new Holder(gunID);
        Inventory inv =
                Bukkit.createInventory(holder, InventoryType.DROPPER, drawn(player) ? SettingsGui.title(title) : title);
        holder.inventory = inv;
        GunColors colors = gunColors.getColors(gunID);
        inv.setItem(0, flat(arrow(colors.getPrimary().next(), true, player), player));
        inv.setItem(2, flat(arrow(colors.getSecondary().next(), true, player), player));
        inv.setItem(6, flat(arrow(colors.getPrimary().previous(), false, player), player));
        inv.setItem(8, flat(arrow(colors.getSecondary().previous(), false, player), player));

        inv.setItem(3, flat(getColorPreview(colors.getPrimary(), player), player));
        inv.setItem(5, flat(getColorPreview(colors.getSecondary(), player), player));

        inv.setItem(1, flat(createConnectionItem(activePortals.isBlocked(gunID), player), player));
        inv.setItem(4, flat(createResetItem(player), player));

        PortalVisualizationType visualizationType = PortalVisualizationType.fromString(
                persistence.get("porticleguns." + ItemHandler.saveable(gunID) + ".shape"));

        boolean ellpitic = visualizationType == PortalVisualizationType.ELLIPTIC;

        String stateKey = ellpitic ? "menus.settings.round-on" : "menus.settings.round-off";
        String roundState = MessageManager.getMessage(player, stateKey);
        String roundLabel =
                MessageManager.getMessage(player, "menus.settings.round-label", Map.of("state", roundState));

        inv.setItem(
                7,
                flat(
                        new BannerBuilder()
                                .setBaseColor(DyeColor.WHITE)
                                .setPatterns(
                                        new Pattern(
                                                ellpitic ? DyeColor.GRAY : DyeColor.RED, PatternType.HALF_HORIZONTAL),
                                        new Pattern(
                                                ellpitic ? DyeColor.GREEN : DyeColor.GRAY,
                                                PatternType.HALF_HORIZONTAL_BOTTOM),
                                        new Pattern(
                                                ellpitic ? DyeColor.GREEN : DyeColor.RED, PatternType.STRIPE_MIDDLE),
                                        new Pattern(DyeColor.LIGHT_GRAY, PatternType.BORDER))
                                .hideItemFlags()
                                .setName(roundLabel)
                                .build(),
                        player));

        return inv;
    }

    private ItemStack createResetItem(Player player) {
        ItemStack reset = resetTemplate.clone();
        ItemMeta meta = reset.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageManager.getMessage(player, "menus.settings.reset-name"));
            String loreLine = MessageManager.getMessage(player, "menus.settings.reset-lore");
            if (loreLine != null && !loreLine.isEmpty()) {
                meta.setLore(Collections.singletonList(loreLine));
            } else {
                meta.setLore(null);
            }
            reset.setItemMeta(meta);
        }
        return reset;
    }

    @EventHandler
    public void on(InventoryCloseEvent event) {
        String gunID = isPorticleSettingsInv(event.getView());
        if (gunID != null) {
            GunColors colors = gunColors.getColors(gunID);
            Portal.saveColor(persistence, gunID, Portal.PortalType.PRIMARY, colors.getPrimary());
            Portal.saveColor(persistence, gunID, Portal.PortalType.SECONDARY, colors.getSecondary());
            AudioHandler.playSound((Player) event.getPlayer(), AudioHandler.PortalSound.INV_CLOSE);
        }
    }

    /**
     * Checks if the inventory is a portal settings inventory
     * @param view the inventory view
     * @return the gun id if the inventory is a portal settings inventory, null otherwise
     */
    private String isPorticleSettingsInv(InventoryView view) {
        return view.getTopInventory().getHolder() instanceof Holder holder ? holder.gunID : null;
    }
}
