package eu.nurkert.porticlegun.handlers.portals;

import eu.nurkert.porticlegun.builders.BannerBuilder;
import eu.nurkert.porticlegun.builders.ItemBuilder;
import eu.nurkert.porticlegun.handlers.AudioHandler;
import eu.nurkert.porticlegun.handlers.visualization.GunColorHandler;
import eu.nurkert.porticlegun.handlers.visualization.GunColors;
import eu.nurkert.porticlegun.handlers.item.ItemHandler;
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

public class ChangeColorHandler implements Listener {

    final ItemStack up = new BannerBuilder().setName("§f▲")
            .setPatterns(new Pattern(DyeColor.LIGHT_GRAY, PatternType.DIAGONAL_LEFT),
                    new Pattern(DyeColor.LIGHT_GRAY, PatternType.DIAGONAL_RIGHT_MIRROR))
            .hideItemFlags().build();
    final ItemStack down = new BannerBuilder().setName("§f▼")
            .setPatterns(new Pattern(DyeColor.LIGHT_GRAY, PatternType.DIAGONAL_RIGHT),
                    new Pattern(DyeColor.LIGHT_GRAY, PatternType.DIAGONAL_LEFT_MIRROR))
            .hideItemFlags().build();

    private final static String INVENTORY_TITLE = "§8Settings";
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getType() == InventoryType.DROPPER && event.getRawSlot() < 9) {
            String gunID = isPorticleSettingsInv(event.getView());
            if (gunID != null) {
                event.setCancelled(true);
                if (event.getCurrentItem() != null) {
                    if (event.getClick() == ClickType.DOUBLE_CLICK &&  event.getCurrentItem().getType() == Material.BARRIER) {
                        ActivePortalsHandler.removePrimaryPortal(gunID);
                        ActivePortalsHandler.removeSecondaryPortal(gunID);
                        AudioHandler.playSound((Player) event.getWhoClicked(), AudioHandler.PortalSound.PORTAL_CLOSE);
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
                    } else {
                        return;
                    }
                    AudioHandler.playSound((Player) event.getWhoClicked(), AudioHandler.PortalSound.CLICK);
                }
            }
        }
    }

    public ItemStack getColorPreview(PortalColor color) {
        return new BannerBuilder().setName(color.getChatColor() + color.toString())
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

        ItemStack background =  new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(ItemHandler.encodeID(gunID)).build();
        for (int i = 1; i <= 3; i++)
            inv.setItem((i * 3) - 2, background);

        inv.setItem(4, new ItemBuilder(Material.BARRIER).setName("§c§lRESET").setLore("§8§o(requires double click)").build());

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
                    String gunID = ItemHandler.decodeID(item.getItemMeta().getDisplayName());
                    if(ItemHandler.isValidGunID(gunID)) {
                        return gunID;
                    }
                }
            }
        }
        return null;
    }
}
