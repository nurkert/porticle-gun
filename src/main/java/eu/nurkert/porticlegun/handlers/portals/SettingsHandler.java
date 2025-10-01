package eu.nurkert.porticlegun.handlers.portals;

import eu.nurkert.porticlegun.builders.BannerBuilder;
import eu.nurkert.porticlegun.builders.ItemBuilder;
import eu.nurkert.porticlegun.handlers.AudioHandler;
import eu.nurkert.porticlegun.handlers.PersitentHandler;
import eu.nurkert.porticlegun.handlers.visualization.GunColorHandler;
import eu.nurkert.porticlegun.handlers.visualization.GunColors;
import eu.nurkert.porticlegun.handlers.item.ItemHandler;
import eu.nurkert.porticlegun.handlers.visualization.concrete.PortalVisualization;
import eu.nurkert.porticlegun.handlers.visualization.concrete.PortalVisualizationType;
import eu.nurkert.porticlegun.portals.Portal;
import eu.nurkert.porticlegun.handlers.visualization.PortalColor;
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

import javax.sound.sampled.Port;
import java.util.HashMap;

public class SettingsHandler implements Listener {

    final ItemStack up = new BannerBuilder().setBaseColor(DyeColor.GRAY).setName("§f▲")
            .setPatterns(new Pattern(DyeColor.LIGHT_GRAY, PatternType.DIAGONAL_LEFT),
                    new Pattern(DyeColor.LIGHT_GRAY, PatternType.DIAGONAL_UP_RIGHT))
            .hideItemFlags().build();
    final ItemStack down = new BannerBuilder().setBaseColor(DyeColor.GRAY).setName("§f▼")
            .setPatterns(new Pattern(DyeColor.LIGHT_GRAY, PatternType.DIAGONAL_RIGHT),
                    new Pattern(DyeColor.LIGHT_GRAY, PatternType.DIAGONAL_UP_LEFT))
            .hideItemFlags().build();

    final ItemStack reset = new BannerBuilder().setBaseColor(DyeColor.LIGHT_GRAY).setName("§c§lRemove portals")
            .setLore("§8§o(requires double click)").setPatterns(new Pattern(DyeColor.RED, PatternType.CROSS),
                    new Pattern(DyeColor.LIGHT_GRAY, PatternType.BORDER))
            .hideItemFlags().build();

    private final static String INVENTORY_TITLE = "§8Settings";

    HashMap<String, Long> lastClick = new HashMap<>();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getType() == InventoryType.DROPPER && event.getRawSlot() < 9) {
            String gunID = isPorticleSettingsInv(event.getView());
            if (gunID != null) {
                event.setCancelled(true);
                if (event.getCurrentItem() != null) {
                    if ( event.getRawSlot() == 4) {
                        if(lastClick.containsKey(gunID) && System.currentTimeMillis() - lastClick.get(gunID) < 500) {
                            ActivePortalsHandler.removePrimaryPortal(gunID);
                            ActivePortalsHandler.removeSecondaryPortal(gunID);
                            AudioHandler.playSound((Player) event.getWhoClicked(), AudioHandler.PortalSound.PORTAL_CLOSE);
                        } else {
                            lastClick.put(gunID, System.currentTimeMillis());
                        }
                    } else if(event.getRawSlot() == 0) {
                        GunColorHandler.selectNextPrimary(gunID);
                        Inventory inv = getCurrentSettings(gunID);
                        event.getClickedInventory().setContents(inv.getContents());
                    } else if(event.getRawSlot() == 6) {
                        GunColorHandler.selectPreviousPrimary(gunID);
                        Inventory inv = getCurrentSettings(gunID);
                        event.getClickedInventory().setContents(inv.getContents());
                    } else if(event.getRawSlot() == 2) {
                        GunColorHandler.selectNextSecondary(gunID);
                        Inventory inv = getCurrentSettings(gunID);
                        event.getClickedInventory().setContents(inv.getContents());
                    } else if(event.getRawSlot() == 8) {
                        GunColorHandler.selectPreviousSecondary(gunID);
                        Inventory inv = getCurrentSettings(gunID);
                        event.getClickedInventory().setContents(inv.getContents());
                    } else if (event.getRawSlot() == 7) {

                        Portal[] portals = new Portal[]{ActivePortalsHandler.getPrimaryPortal(gunID), ActivePortalsHandler.getSecondaryPortal(gunID)};

                        for(Portal portal : portals)
                            if(portal != null)
                                portal.toggleVisualizationType();

                        PortalVisualizationType visualizationType = PortalVisualizationType.fromString(PersitentHandler.get("porticleguns." + ItemHandler.saveable(gunID) + ".shape"));
                        PersitentHandler.set("porticleguns." + ItemHandler.saveable(gunID) + ".shape", visualizationType.getNext().toString());

                        Inventory inv = getCurrentSettings(gunID);
                        event.getClickedInventory().setContents(inv.getContents());
                    } else {
                        return;
                    }
                    AudioHandler.playSound((Player) event.getWhoClicked(), AudioHandler.PortalSound.CLICK);
                }
            }
        }
    }

    public ItemStack getColorPreview(PortalColor color) {
        return new BannerBuilder().setBaseColor(DyeColor.LIGHT_GRAY).setName(color.getChatColor() + color.toString())
                .setPatterns(new Pattern(color.getDyeColor(), PatternType.BORDER))
                .hideItemFlags().build();
    }

    @EventHandler
    public void on(PlayerInteractEvent event) {
        // check if player is holding a gun and is sneaking
        if(event.getItem() != null && event.getPlayer().isSneaking()) {
            // check if player is holding a gun
            String gunID = ItemHandler.isValidGun(event.getItem());
            if(gunID != null) {
                Player player = event.getPlayer();

                AudioHandler.playSound(player, AudioHandler.PortalSound.INV_OPEN);
                Inventory inv = getCurrentSettings(gunID);
                player.openInventory(inv);
            }
        }
    }

    /**
     * Get an inventory with the current settings of the gun
     * @param gunID the id of the gun
     * @return
     */
    public Inventory getCurrentSettings(String gunID) {
        Inventory inv = Bukkit.createInventory(null, InventoryType.DROPPER, INVENTORY_TITLE);
        inv.setItem(0, up);
        inv.setItem(2, up);
        inv.setItem(6, down);
        inv.setItem(8, down);

        GunColors colors = GunColorHandler.getColors(gunID);

        inv.setItem(3, getColorPreview(colors.getPrimary()));
        inv.setItem(5, getColorPreview(colors.getSecondary()));

        ItemStack background =  new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(ItemHandler.vanishID(gunID)).build();
        for (int i = 1; i <= 3; i++)
            inv.setItem((i * 3) - 2, background);

        inv.setItem(4, reset);

        PortalVisualizationType visualizationType = PortalVisualizationType.fromString(PersitentHandler.get("porticleguns." + ItemHandler.saveable(gunID) + ".shape"));

        boolean ellpitic = visualizationType == PortalVisualizationType.ELLIPTIC;

        inv.setItem(7, new BannerBuilder().setBaseColor(DyeColor.WHITE).
                setPatterns(new Pattern(ellpitic ? DyeColor.GRAY : DyeColor.RED, PatternType.HALF_HORIZONTAL),
                        new Pattern(ellpitic ? DyeColor.GREEN : DyeColor.GRAY, PatternType.HALF_HORIZONTAL_BOTTOM),
                        new Pattern(ellpitic ? DyeColor.GREEN : DyeColor.RED, PatternType.STRIPE_MIDDLE),
                        new Pattern(DyeColor.LIGHT_GRAY, PatternType.BORDER))
                .hideItemFlags().setName("§7Round: " + (ellpitic ? "§2ON" : "§4OFF")).build());

        return inv;
    }

    @EventHandler
    public void on(InventoryCloseEvent event) {
        String gunID = isPorticleSettingsInv(event.getView());
        if(gunID != null) {
            GunColors colors = GunColorHandler.getColors(gunID);
            Portal.saveColor(gunID, Portal.PortalType.PRIMARY, colors.getPrimary());
            Portal.saveColor(gunID, Portal.PortalType.SECONDARY, colors.getSecondary());
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
        if (title.startsWith(INVENTORY_TITLE)) {
            Inventory inv = view.getTopInventory();
            if(inv.getItem(1).getType() == Material.GRAY_STAINED_GLASS_PANE) {
                ItemStack item = inv.getItem(1);
                if(item.hasItemMeta() && item.getItemMeta().hasDisplayName() && item.getItemMeta().getDisplayName().contains("§")) {
                    String gunID = ItemHandler.revealID(item.getItemMeta().getDisplayName());
                    if(ItemHandler.isValidGunID(gunID)) {
                        return gunID;
                    }
                }
            }
        }
        return null;
    }
}
