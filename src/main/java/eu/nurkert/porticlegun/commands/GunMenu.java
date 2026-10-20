package eu.nurkert.porticlegun.commands;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.builders.ItemBuilder;
import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.util.PluginJarRenamer;
import eu.nurkert.porticlegun.util.PluginJarRenamer.RenameResult;
import java.util.Map;
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
 * The menu opened by {@code /porticlegun}: hands out a new gun and lets admins rename the plugin jar.
 */
public class GunMenu implements Listener {

    private static final int QUICK_RENAME_SLOT = 0;
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
        ItemStack renameItem = createQuickRenameItem(player);
        if (renameItem != null) {
            inventory.setItem(QUICK_RENAME_SLOT, renameItem);
        }
        inventory.setItem(GUN_SLOT, ItemHandler.generateNewGun());
        return inventory;
    }

    private ItemStack createFiller(Player player) {
        return new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
                .setName(MessageManager.getMessage(player, "menus.porticlegun.filler-name"))
                .build();
    }

    private ItemStack createQuickRenameItem(Player player) {
        if (!player.hasPermission(Permissions.ADMIN)) {
            return null;
        }
        if (!PluginJarRenamer.isRenameAvailable(PorticleGun.getInstance())) {
            return null;
        }

        String target = PluginJarRenamer.getTargetFileName(PorticleGun.getInstance());
        String name = MessageManager.getMessage(player, "menus.porticlegun.rename-jar-name", Map.of("target", target));
        String loreRaw =
                MessageManager.getMessage(player, "menus.porticlegun.rename-jar-lore", Map.of("target", target));

        ItemBuilder builder = new ItemBuilder(Material.NAME_TAG).setName(name);
        String[] lore = splitLore(loreRaw);
        if (lore.length > 0) {
            builder.setLore(lore);
        }
        return builder.build();
    }

    private String[] splitLore(String loreRaw) {
        if (loreRaw == null || loreRaw.isEmpty()) {
            return new String[0];
        }
        return loreRaw.split("\n");
    }

    private void updateRenameSlot(Inventory inventory, Player player) {
        ItemStack renameItem = createQuickRenameItem(player);
        if (renameItem != null) {
            inventory.setItem(QUICK_RENAME_SLOT, renameItem);
        } else {
            inventory.setItem(QUICK_RENAME_SLOT, createFiller(player));
        }
    }

    @EventHandler
    public void on(InventoryClickEvent event) {
        if (MessageManager.matchesConfiguredValue(
                "menus.porticlegun.title", event.getView().getTitle())) {
            if (event.getRawSlot() < event.getInventory().getSize()) {
                event.setCancelled(true);
                if (event.getRawSlot() == GUN_SLOT) {
                    event.setCursor(ItemHandler.generateNewGun());
                } else if (event.getRawSlot() == QUICK_RENAME_SLOT && event.getWhoClicked() instanceof Player) {
                    handleQuickRename((Player) event.getWhoClicked(), event.getInventory());
                }
            }
        }
    }

    private void handleQuickRename(Player player, Inventory inventory) {
        if (!player.hasPermission(Permissions.ADMIN)) {
            player.sendMessage(MessageManager.getMessage(player, "commands.admin.no-permission"));
            return;
        }

        PorticleGun plugin = PorticleGun.getInstance();
        String target = PluginJarRenamer.getTargetFileName(plugin);
        Map<String, String> placeholders = Map.of("target", target);
        RenameResult result = PluginJarRenamer.renameToDataFolder(plugin);

        switch (result) {
            case SUCCESS:
                player.sendMessage(
                        MessageManager.getMessage(player, "menus.porticlegun.rename-jar-success", placeholders));
                player.closeInventory();
                break;
            case ALREADY_MATCHED:
                player.sendMessage(
                        MessageManager.getMessage(player, "menus.porticlegun.rename-jar-already", placeholders));
                updateRenameSlot(inventory, player);
                break;
            case CONFLICT:
                player.sendMessage(
                        MessageManager.getMessage(player, "menus.porticlegun.rename-jar-conflict", placeholders));
                break;
            case UNAVAILABLE:
                player.sendMessage(MessageManager.getMessage(player, "menus.porticlegun.rename-jar-unavailable"));
                updateRenameSlot(inventory, player);
                break;
            case ERROR:
            default:
                player.sendMessage(MessageManager.getMessage(player, "menus.porticlegun.rename-jar-error"));
                break;
        }
    }
}
