package eu.nurkert.porticlegun.commands;

import eu.nurkert.porticlegun.builders.ItemBuilder;
import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.messages.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * The menu opened by {@code /porticlegun}: hands out a new gun.
 */
public class GunMenu implements Listener {

    private static final int GUN_SLOT = 4;

    public void open(Player player) {
        player.openInventory(createMenu(player));
    }

    private Inventory createMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(
                null, InventoryType.DROPPER, MessageManager.getMessage(player, "menus.porticlegun.title"));
        ItemStack filler = createFiller(player);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i == GUN_SLOT) {
                continue;
            }
            inventory.setItem(i, filler.clone());
        }
        inventory.setItem(GUN_SLOT, ItemHandler.generateNewGun());
        return inventory;
    }

    private ItemStack createFiller(Player player) {
        return new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
                .setName(MessageManager.getMessage(player, "menus.porticlegun.filler-name"))
                .build();
    }

    @EventHandler
    public void on(InventoryClickEvent event) {
        if (MessageManager.matchesConfiguredValue(
                "menus.porticlegun.title", event.getView().getTitle())) {
            if (event.getRawSlot() < event.getInventory().getSize()) {
                event.setCancelled(true);
                if (event.getRawSlot() == GUN_SLOT) {
                    event.setCursor(ItemHandler.generateNewGun());
                }
            }
        }
    }
}
