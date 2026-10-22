package eu.nurkert.porticlegun.portals;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.audio.AudioHandler;
import eu.nurkert.porticlegun.builders.BannerBuilder;
import eu.nurkert.porticlegun.builders.ItemBuilder;
import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.persistence.PersistentHandler;
import eu.nurkert.porticlegun.visualization.GunColorHandler;
import eu.nurkert.porticlegun.visualization.GunColors;
import eu.nurkert.porticlegun.visualization.PortalColor;
import eu.nurkert.porticlegun.visualization.concrete.PortalVisualizationType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
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

    public SettingsHandler(
            ActivePortalsHandler activePortals, GunColorHandler gunColors, PersistentHandler persistence) {
        this.activePortals = activePortals;
        this.gunColors = gunColors;
        this.persistence = persistence;
    }

    final ItemStack resetTemplate = new BannerBuilder()
            .setBaseColor(DyeColor.LIGHT_GRAY)
            .setPatterns(
                    new Pattern(DyeColor.RED, PatternType.CROSS), new Pattern(DyeColor.LIGHT_GRAY, PatternType.BORDER))
            .hideItemFlags()
            .build();

    HashMap<String, Long> lastClick = new HashMap<>();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getType() == InventoryType.DROPPER && event.getRawSlot() < 9) {
            String gunID = isPorticleSettingsInv(event.getView());
            if (gunID != null) {
                event.setCancelled(true);
                if (event.getCurrentItem() != null) {
                    Player player = (Player) event.getWhoClicked();
                    if (event.getRawSlot() == 4) {
                        if (lastClick.containsKey(gunID) && System.currentTimeMillis() - lastClick.get(gunID) < 500) {
                            activePortals.closePortals(gunID);
                            AudioHandler.playSound(
                                    (Player) event.getWhoClicked(), AudioHandler.PortalSound.PORTAL_CLOSE);
                        } else {
                            lastClick.put(gunID, System.currentTimeMillis());
                        }
                    } else if (event.getRawSlot() == 0 || event.getRawSlot() == 6) {
                        PortalColor before = gunColors.getColors(gunID).getPrimary();
                        if (event.getRawSlot() == 0) {
                            gunColors.selectNextPrimary(gunID);
                        } else {
                            gunColors.selectPreviousPrimary(gunID);
                        }
                        changeColour(event.getClickedInventory(), gunID, player, 3, before, event.getRawSlot() == 6);
                    } else if (event.getRawSlot() == 2 || event.getRawSlot() == 8) {
                        PortalColor before = gunColors.getColors(gunID).getSecondary();
                        if (event.getRawSlot() == 2) {
                            gunColors.selectNextSecondary(gunID);
                        } else {
                            gunColors.selectPreviousSecondary(gunID);
                        }
                        changeColour(event.getClickedInventory(), gunID, player, 5, before, event.getRawSlot() == 8);
                    } else if (event.getRawSlot() == 7) {

                        Portal[] portals = new Portal[] {
                            activePortals.getPrimaryPortal(gunID), activePortals.getSecondaryPortal(gunID)
                        };

                        for (Portal portal : portals) if (portal != null) portal.toggleVisualizationType();

                        PortalVisualizationType visualizationType = PortalVisualizationType.fromString(
                                persistence.get("porticleguns." + ItemHandler.saveable(gunID) + ".shape"));
                        persistence.set(
                                "porticleguns." + ItemHandler.saveable(gunID) + ".shape",
                                visualizationType.getNext().toString());

                        Inventory inv = getCurrentSettings(gunID, player);
                        event.getClickedInventory().setContents(inv.getContents());
                    } else {
                        return;
                    }
                    AudioHandler.playSound((Player) event.getWhoClicked(), AudioHandler.PortalSound.CLICK);
                }
            }
        }
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
     * lower half (below it).
     *
     * @param up an arrow pointing up (next colour) or down (previous colour)
     * @param background the banner's background, black unless the arrow is shown in the middle of a colour change
     */
    private static ItemStack arrow(PortalColor target, boolean up, DyeColor background, Player player) {
        DyeColor dye = target.getDyeColor();
        return new BannerBuilder()
                .setBaseColor(background)
                .setName((up ? "§f▲ " : "§f▼ ") + target.getChatColor() + target.displayName(player))
                .setPatterns(
                        // a diamond with its lower (or upper) half covered leaves a triangle with clean, sharp edges
                        new Pattern(dye, PatternType.RHOMBUS),
                        new Pattern(background, up ? PatternType.HALF_HORIZONTAL_BOTTOM : PatternType.HALF_HORIZONTAL))
                .hideItemFlags()
                .build();
    }

    /** The resting background of an arrow: black, or grey behind a black arrow so it can be seen. */
    private static DyeColor arrowBackground(PortalColor target) {
        return target.getDyeColor() == DyeColor.BLACK ? DyeColor.GRAY : DyeColor.BLACK;
    }

    private static ItemStack arrow(PortalColor target, boolean up, Player player) {
        return arrow(target, up, arrowBackground(target), player);
    }

    // running colour animations, by menu and colour banner slot, so a fast second click restarts instead of overlapping
    private final Map<Inventory, Map<Integer, BukkitTask>> animations = new HashMap<>();

    /**
     * Shows a new colour in four frames, two ticks apart: the colour banner fills with the new colour, from below
     * after the down arrow and from above after the up arrow, while the two arrows fade from the colour they showed
     * before over grey to their resting background, already showing their new colours.
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
            fill(oldDye, new Pattern(newDye, edge), name),
            fill(oldDye, new Pattern(newDye, half), name),
            fill(newDye, new Pattern(oldDye, rest), name),
            getColorPreview(after, player)
        };
        PortalColor next = after.next();
        PortalColor previousColour = after.previous();
        DyeColor[] upBackgrounds = {before.next().getDyeColor(), DyeColor.GRAY, arrowBackground(next)};
        DyeColor[] downBackgrounds = {before.previous().getDyeColor(), DyeColor.GRAY, arrowBackground(previousColour)};
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
                        if (frame < upBackgrounds.length) {
                            inventory.setItem(column, arrow(next, true, upBackgrounds[frame], player));
                            inventory.setItem(column + 6, arrow(previousColour, false, downBackgrounds[frame], player));
                        }
                        frame++;
                    }
                }.runTaskTimer(PorticleGun.getInstance(), 0L, 2L));
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
        Inventory inv = Bukkit.createInventory(
                null, InventoryType.DROPPER, MessageManager.getMessage(player, "menus.settings.title"));
        GunColors colors = gunColors.getColors(gunID);
        inv.setItem(0, arrow(colors.getPrimary().next(), true, player));
        inv.setItem(2, arrow(colors.getSecondary().next(), true, player));
        inv.setItem(6, arrow(colors.getPrimary().previous(), false, player));
        inv.setItem(8, arrow(colors.getSecondary().previous(), false, player));

        inv.setItem(3, getColorPreview(colors.getPrimary(), player));
        inv.setItem(5, getColorPreview(colors.getSecondary(), player));

        ItemStack background = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setName(ItemHandler.vanishID(gunID))
                .build();
        for (int i = 1; i <= 3; i++) inv.setItem((i * 3) - 2, background);

        inv.setItem(4, createResetItem(player));

        PortalVisualizationType visualizationType = PortalVisualizationType.fromString(
                persistence.get("porticleguns." + ItemHandler.saveable(gunID) + ".shape"));

        boolean ellpitic = visualizationType == PortalVisualizationType.ELLIPTIC;

        String stateKey = ellpitic ? "menus.settings.round-on" : "menus.settings.round-off";
        String roundState = MessageManager.getMessage(player, stateKey);
        String roundLabel =
                MessageManager.getMessage(player, "menus.settings.round-label", Map.of("state", roundState));

        inv.setItem(
                7,
                new BannerBuilder()
                        .setBaseColor(DyeColor.WHITE)
                        .setPatterns(
                                new Pattern(ellpitic ? DyeColor.GRAY : DyeColor.RED, PatternType.HALF_HORIZONTAL),
                                new Pattern(
                                        ellpitic ? DyeColor.GREEN : DyeColor.GRAY, PatternType.HALF_HORIZONTAL_BOTTOM),
                                new Pattern(ellpitic ? DyeColor.GREEN : DyeColor.RED, PatternType.STRIPE_MIDDLE),
                                new Pattern(DyeColor.LIGHT_GRAY, PatternType.BORDER))
                        .hideItemFlags()
                        .setName(roundLabel)
                        .build());

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
        String title = view.getTitle();
        if (MessageManager.matchesConfiguredValue("menus.settings.title", title)) {
            Inventory inv = view.getTopInventory();
            ItemStack slot = inv.getItem(1);
            if (slot != null && slot.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                if (slot.hasItemMeta()
                        && slot.getItemMeta().hasDisplayName()
                        && slot.getItemMeta().getDisplayName().contains("§")) {
                    String gunID = ItemHandler.revealID(slot.getItemMeta().getDisplayName());
                    if (ItemHandler.isValidGunID(gunID)) {
                        return gunID;
                    }
                }
            }
        }
        return null;
    }
}
