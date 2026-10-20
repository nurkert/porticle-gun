package eu.nurkert.porticlegun.item;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Converts guns created before 1.3 (id hidden in the lore) whenever they show up in an inventory. Old guns keep
 * working without it, this only cleans up their lore.
 */
public class LegacyGunMigration implements Listener {

    @EventHandler
    public void on(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        migrate(player.getInventory());
        migrate(player.getEnderChest());
    }

    @EventHandler
    public void on(PlayerItemHeldEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (ItemHandler.migrateLegacyGun(item)) {
            event.getPlayer().getInventory().setItem(event.getNewSlot(), item);
        }
    }

    @EventHandler
    public void on(InventoryOpenEvent event) {
        migrate(event.getInventory());
    }

    private void migrate(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (ItemStack item : contents) {
            changed |= ItemHandler.migrateLegacyGun(item);
        }
        if (changed) {
            inventory.setContents(contents);
        }
    }
}
