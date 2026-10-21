package eu.nurkert.porticlegun.commands;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.builders.ItemBuilder;
import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.rift.RiftItems;
import eu.nurkert.porticlegun.rift.RiftModule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The menu opened by {@code /porticlegun}: every PorticleGun item in one chest. A single click (or tap) puts the item
 * into the inventory. Everyone gets the PorticleGun; admins also get the interdimensional gun and its items.
 */
public class GunMenu implements Listener {

    private static final int SIZE = 27;
    private static final int[] ADMIN_SLOTS = {11, 12, 13, 14, 15};
    private static final int PLAYER_SLOT = 13;

    /** Marks our inventories, so clicks are recognised without comparing titles. */
    private static final class Holder implements InventoryHolder {
        private final Map<Integer, Supplier<ItemStack>> items = new LinkedHashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inventory =
                Bukkit.createInventory(holder, SIZE, MessageManager.getMessage(player, "menus.porticlegun.title"));
        holder.inventory = inventory;
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setName(MessageManager.getMessage(player, "menus.porticlegun.filler-name"))
                .build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        List<Supplier<ItemStack>> items = new ArrayList<>();
        items.add(ItemHandler::generateNewGun);
        RiftModule rift = PorticleGun.getInstance().getRiftModule();
        if (rift != null && player.hasPermission(Permissions.ADMIN)) {
            items.add(() -> RiftItems.create(RiftItems.Type.INTERDIMENSIONAL_GUN, 1, rift.settings()));
            items.add(() -> RiftItems.create(RiftItems.Type.RIFT_SHARD, 16, rift.settings()));
            items.add(() -> RiftItems.create(RiftItems.Type.PORTAL_FLUID, 4, rift.settings()));
            items.add(() -> RiftItems.create(RiftItems.Type.RIFT_COMPASS, 1, rift.settings()));
        }
        int[] slots = items.size() == 1 ? new int[] {PLAYER_SLOT} : ADMIN_SLOTS;
        for (int i = 0; i < items.size(); i++) {
            holder.items.put(slots[i], items.get(i));
            inventory.setItem(slots[i], withHint(items.get(i).get(), player));
        }
        player.openInventory(inventory);
    }

    /** The item as shown in the menu: the real item with a "click to take" line below its lore. */
    private static ItemStack withHint(ItemStack item, Player player) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(MessageManager.getMessage(player, "menus.porticlegun.take"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void on(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        // nothing moves in or out of the menu, whatever the click
        event.setCancelled(true);
        if (event.getRawSlot() >= SIZE || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Supplier<ItemStack> item = holder.items.get(event.getRawSlot());
        if (item == null) {
            return;
        }
        for (ItemStack leftover : player.getInventory().addItem(item.get()).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
    }

    @EventHandler
    public void on(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
