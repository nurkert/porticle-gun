package eu.nurkert.porticlegun.commands;

import eu.nurkert.porticlegun.builders.ItemBuilder;
import eu.nurkert.porticlegun.handlers.item.ItemHandler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

public class PorticleGunCommand implements CommandExecutor, Listener {


    private static final String TITLE = "§8PorticleGun";
    private static final String COMMAND_PERMISSION = "porticlegun.command";

    private final Inventory inv;

    public PorticleGunCommand() {
        inv = Bukkit.createInventory(null, InventoryType.DROPPER, TITLE);
        init();
    }

    private void init() {
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName("§0|").build());
        }
        inv.setItem(4, ItemHandler.generateNewGun());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can execute this command.");
            return true;
        }
        Player player = (Player) sender;

        if (!player.hasPermission(COMMAND_PERMISSION)) {
            player.sendMessage("§cDu hast keine Berechtigung, diese Aktion auszuführen.");
            return true;
        }

        player.openInventory(inv);
        return true;
    }

    @EventHandler
    public void on(InventoryClickEvent event) {
        if (TITLE.equals(event.getView().getTitle())) {
            if (event.getRawSlot() < event.getInventory().getSize()) {
                event.setCancelled(true);
                if (event.getRawSlot() == 4) {
                    event.setCursor(ItemHandler.generateNewGun());
                }
            }
        }
    }

    @EventHandler
    public void on(InventoryCloseEvent event) {
        if (TITLE.equals(event.getView().getTitle())) {
            //SoundHandler.playSound(event.getPlayer().getEyeLocation(), APGSound.INV_CLOSE);
        }
    }
}
