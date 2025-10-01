package eu.nurkert.porticlegun.handlers.visualization;

import eu.nurkert.porticlegun.handlers.item.ItemHandler;
import eu.nurkert.porticlegun.handlers.portals.ActivePortalsHandler;
import eu.nurkert.porticlegun.messages.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class TitleHandler implements Listener {

    @EventHandler
    public void on(InventoryCloseEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item != null) {
            String gunID = ItemHandler.isValidGun(item);
            if(gunID != null) {
                sendPortalStatus((Player) event.getPlayer(), gunID);
            }
        }
    }

    @EventHandler
    public void on(PlayerItemHeldEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (item != null) {
            String gunID = ItemHandler.isValidGun(item);
            if(gunID != null) {
                sendPortalStatus(event.getPlayer(), gunID);
            }
        }
    }

    @EventHandler
    public void on(EntityPickupItemEvent event) {
        if(event.getEntity() instanceof Player) {
            ItemStack item = event.getItem().getItemStack();

            String gunID = ItemHandler.isValidGun(item);
            if(gunID != null) {
                sendPortalStatus((Player) event.getEntity(), gunID);
            }
        }
    }

    @EventHandler
    public void on(PlayerSwapHandItemsEvent event) {
        ItemStack item = event.getMainHandItem();
        if (item != null) {
            String gunID = ItemHandler.isValidGun(item);
            if(gunID != null) {
                sendPortalStatus(event.getPlayer(), gunID);
            }
        }
    }

    public static void sendPortalStatus(Player player, String gunID) {
        String primary = GunColorHandler.getColors(gunID).getPrimary().getChatColor() + "§o" + (ActivePortalsHandler.hasPrimaryPortal(gunID) ? "§l◀" : "◁");
        String secondary = GunColorHandler.getColors(gunID).getSecondary().getChatColor() + "§o" + (ActivePortalsHandler.hasSecondaryPortal(gunID) ? "§l▶" : "▷");

        String title = MessageManager.getMessage(player, "titles.portal-status", Map.of(
                "primary", primary,
                "secondary", secondary
        ));
        String subtitle = MessageManager.getMessage(player, "titles.portal-status-subtitle");
        player.sendTitle(title, subtitle, 0, 20 * 5, 20);
    }
}
