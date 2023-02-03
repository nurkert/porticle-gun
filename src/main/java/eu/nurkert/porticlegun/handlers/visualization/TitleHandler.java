package eu.nurkert.porticlegun.handlers.visualization;

import eu.nurkert.porticlegun.handlers.item.ItemHandler;
import eu.nurkert.porticlegun.handlers.portals.OpenedPortalsHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

public class TitleHandler implements Listener {

    @EventHandler
    public void on(InventoryCloseEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item != null) {
            String gunID = ItemHandler.isValidGun(item);
            if(gunID != null)
                sendPortalStatus((Player) event.getPlayer(), gunID);
        }
    }

    @EventHandler
    public void on(PlayerItemHeldEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (item != null) {
            String gunID = ItemHandler.isValidGun(item);
            sendPortalStatus(event.getPlayer(), gunID);
        }

    }

    @EventHandler
    public void on(EntityPickupItemEvent event) {
        if(event.getEntity() instanceof Player) {
            ItemStack item = event.getItem().getItemStack();

            String gunID = ItemHandler.isValidGun(item);
            if(gunID != null) {
               // if(((Player) event.getEntity()).getInventory().getItemInMainHand().equals(item))
                sendPortalStatus((Player) event.getEntity(), gunID);
            }
        }
    }

    public static void sendPortalStatus(Player player, String gunID) {
        String primary = GunColorHandler.getColors(gunID).getPrimary().getChatColor() + "§o" + (OpenedPortalsHandler.hasPrimaryPortal(gunID) ? "§l◀" : "◁");
        String secondary = GunColorHandler.getColors(gunID).getSecondary().getChatColor() + "§o" + (OpenedPortalsHandler.hasSecondaryPortal(gunID) ? "§l▶" : "▷");

        player.sendTitle(primary + " " + secondary, "", 0, 20 * 5, 20);
    }
}
