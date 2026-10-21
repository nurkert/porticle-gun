package eu.nurkert.porticlegun.item;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.rift.RiftItems;
import eu.nurkert.porticlegun.rift.RiftModule;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Keeps gun items up to date whenever they show up in an inventory: converts guns created before 1.3 (id hidden in the
 * lore) and refreshes the 3D model and its portal colours, e.g. after the colours were changed in the settings menu.
 * Runs on join, slot changes, picking up, using the gun and opening or closing inventories.
 */
public class GunItemUpdater implements Listener {

    @EventHandler
    public void on(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        update(player.getInventory());
        update(player.getEnderChest());
    }

    @EventHandler
    public void on(PlayerItemHeldEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (update(item)) {
            event.getPlayer().getInventory().setItem(event.getNewSlot(), item);
        }
    }

    @EventHandler
    public void on(EntityPickupItemEvent event) {
        // a gun picked up into the selected slot would otherwise only be updated on the next slot change
        ItemStack item = event.getItem().getItemStack();
        if (update(item)) {
            event.getItem().setItemStack(item);
        }
    }

    @EventHandler
    public void on(PlayerInteractEvent event) {
        if (event.getHand() == null || event.getItem() == null) {
            return;
        }
        ItemStack item = event.getItem();
        if (update(item)) {
            event.getPlayer().getInventory().setItem(event.getHand(), item);
        }
    }

    @EventHandler
    public void on(InventoryOpenEvent event) {
        update(event.getInventory());
    }

    @EventHandler
    public void on(InventoryCloseEvent event) {
        HumanEntity player = event.getPlayer();
        update(player.getInventory());
    }

    private void update(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (ItemStack item : contents) {
            changed |= update(item);
        }
        if (changed) {
            inventory.setContents(contents);
        }
    }

    private boolean update(ItemStack item) {
        boolean changed = ItemHandler.migrateLegacyGun(item);
        String gunID = ItemHandler.isValidGun(item);
        if (gunID != null) {
            changed |= GunAppearance.apply(item, gunID);
        }
        RiftModule rift = PorticleGun.getInstance().getRiftModule();
        if (rift != null && RiftItems.isGun(item)) {
            changed |= RiftItems.refresh(item, rift.settings());
        }
        return changed;
    }
}
