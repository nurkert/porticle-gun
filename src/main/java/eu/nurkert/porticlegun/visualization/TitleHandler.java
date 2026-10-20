package eu.nurkert.porticlegun.visualization;

import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.portals.ActivePortalsHandler;
import eu.nurkert.porticlegun.util.BedrockSupport;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Shows which portals of the held gun are open, as a title in the portal colours.
 */
public class TitleHandler implements Listener {

    @EventHandler
    public void on(InventoryCloseEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item != null) {
            String gunID = ItemHandler.isValidGun(item);
            if (gunID != null) {
                sendPortalStatus((Player) event.getPlayer(), gunID);
            }
        }
    }

    @EventHandler
    public void on(PlayerItemHeldEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (item != null) {
            String gunID = ItemHandler.isValidGun(item);
            if (gunID != null) {
                sendPortalStatus(event.getPlayer(), gunID);
            }
        }
    }

    @EventHandler
    public void on(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            ItemStack item = event.getItem().getItemStack();

            String gunID = ItemHandler.isValidGun(item);
            if (gunID != null) {
                sendPortalStatus((Player) event.getEntity(), gunID);
            }
        }
    }

    @EventHandler
    public void on(PlayerSwapHandItemsEvent event) {
        ItemStack item = event.getMainHandItem();
        if (item != null) {
            String gunID = ItemHandler.isValidGun(item);
            if (gunID != null) {
                sendPortalStatus(event.getPlayer(), gunID);
            }
        }
    }

    public static void sendPortalStatus(Player player, String gunID) {
        String primaryColor = GunColorHandler.getColors(gunID).getPrimary().getChatColor();
        String secondaryColor = GunColorHandler.getColors(gunID).getSecondary().getChatColor();
        boolean primaryOpen = ActivePortalsHandler.hasPrimaryPortal(gunID);
        boolean secondaryOpen = ActivePortalsHandler.hasSecondaryPortal(gunID);

        // the left marker belongs to the button on the left: the mouse's left click, but a controller's left trigger
        // fires the secondary portal
        boolean mirrored = BedrockSupport.hasMirroredTriggers(player);
        String primary = leftMarker(mirrored ? secondaryColor : primaryColor, mirrored ? secondaryOpen : primaryOpen);
        String secondary =
                rightMarker(mirrored ? primaryColor : secondaryColor, mirrored ? primaryOpen : secondaryOpen);

        String title = MessageManager.getMessage(
                player,
                "titles.portal-status",
                Map.of(
                        "primary", primary,
                        "secondary", secondary));
        String subtitle = MessageManager.getMessage(player, "titles.portal-status-subtitle");
        player.sendTitle(title, subtitle, 0, 20 * 5, 20);
    }

    private static String leftMarker(String color, boolean open) {
        return color + "§o" + (open ? "§l◀" : "◁");
    }

    private static String rightMarker(String color, boolean open) {
        return color + "§o" + (open ? "§l▶" : "▷");
    }
}
