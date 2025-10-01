package eu.nurkert.porticlegun.commands;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.builders.ItemBuilder;
import eu.nurkert.porticlegun.handlers.LoadingHandler;
import eu.nurkert.porticlegun.handlers.PersitentHandler;
import eu.nurkert.porticlegun.handlers.item.ItemHandler;
import eu.nurkert.porticlegun.handlers.portals.ActivePortalsHandler;
import eu.nurkert.porticlegun.portals.Portal;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class PorticleGunCommand implements CommandExecutor, Listener, TabCompleter {


    private static final String TITLE = "§8PorticleGun";
    private static final String COMMAND_PERMISSION = "porticlegun.command";
    private static final String ADMIN_PERMISSION = "porticlegun.admin";

    Inventory inv;

    public PorticleGunCommand() {
        inv = Bukkit.createInventory(null, InventoryType.DROPPER, TITLE);
        init();
    }

    private void init() {

        for (int i = 0; i < inv.getSize(); i++)
            inv.setItem(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName("§0|").build());
        inv.setItem(4, ItemHandler.generateNewGun());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                if (player.hasPermission(COMMAND_PERMISSION)) {
                    player.openInventory(inv);
                    //SoundHandler.playSound(player, APGSound.INV_OPEN);
                } else {
                    player.sendMessage("§cYou do not have permission to use this command.");
                }
            } else {
                sender.sendMessage("§cOnly players may open the PorticleGun menu.");
            }
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "list":
                if (!ensureAdmin(sender)) return true;
                handleList(sender);
                return true;
            case "remove":
                if (!ensureAdmin(sender)) return true;
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /" + label + " remove <gunId>");
                    return true;
                }
                handleRemove(sender, args[1]);
                return true;
            case "clearplayer":
                if (!ensureAdmin(sender)) return true;
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /" + label + " clearplayer <player>");
                    return true;
                }
                handleClearPlayer(sender, args[1]);
                return true;
            case "reload":
                if (!ensureAdmin(sender)) return true;
                handleReload(sender);
                return true;
            default:
                sender.sendMessage("§cUnknown subcommand. Available: list, remove, clearplayer, reload");
                return true;
        }
    }

    private boolean ensureAdmin(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return true;
        }
        if (((Player) sender).hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        sender.sendMessage("§cYou do not have permission to manage PorticleGun data.");
        return false;
    }

    private void handleList(CommandSender sender) {
        if (!PersitentHandler.exists("porticleguns")) {
            sender.sendMessage("§eNo stored portal guns found.");
            return;
        }

        List<String> entries = PersitentHandler.getSection("porticleguns");
        if (entries.isEmpty()) {
            sender.sendMessage("§eNo stored portal guns found.");
            return;
        }

        sender.sendMessage("§aStored portal guns (" + entries.size() + "):");
        for (String entry : entries) {
            String gunId = ItemHandler.useable(entry);
            String basePath = "porticleguns." + entry;
            boolean persistedPrimary = PersitentHandler.exists(basePath + ".primary.position");
            boolean persistedSecondary = PersitentHandler.exists(basePath + ".secondary.position");
            boolean activePrimary = ActivePortalsHandler.hasPrimaryPortal(gunId);
            boolean activeSecondary = ActivePortalsHandler.hasSecondaryPortal(gunId);

            String primaryStatus = buildStatus(activePrimary, persistedPrimary);
            String secondaryStatus = buildStatus(activeSecondary, persistedSecondary);

            sender.sendMessage("§7- §f" + gunId + " §8[§9primary: " + primaryStatus + "§8, §dsecondary: " + secondaryStatus + "§8]");
        }
    }

    private String buildStatus(boolean active, boolean persisted) {
        if (active) {
            return "§aactive";
        }
        if (persisted) {
            return "§esaved";
        }
        return "§cnone";
    }

    private void handleRemove(CommandSender sender, String gunId) {
        if (!ItemHandler.isValidGunID(gunId)) {
            sender.sendMessage("§c'" + gunId + "' is not a valid PorticleGun ID.");
            return;
        }

        if (removeGunData(gunId)) {
            sender.sendMessage("§aRemoved stored data for gun §f" + gunId + "§a.");
        } else {
            sender.sendMessage("§eNo stored portals were found for gun §f" + gunId + "§e.");
        }
    }

    private void handleClearPlayer(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage("§cPlayer '" + playerName + "' is not online.");
            return;
        }

        Set<String> gunIds = collectGunIds(target);
        if (gunIds.isEmpty()) {
            sender.sendMessage("§ePlayer §f" + target.getName() + " §ehas no PorticleGuns in their inventories.");
            return;
        }

        List<String> cleared = new ArrayList<>();
        List<String> untouched = new ArrayList<>();

        for (String gunId : gunIds) {
            if (removeGunData(gunId)) {
                cleared.add(gunId);
            } else {
                untouched.add(gunId);
            }
        }

        if (!cleared.isEmpty()) {
            sender.sendMessage("§aCleared portals for §f" + target.getName() + "§a: §7" + String.join("§8, §7", cleared));
        }

        if (!untouched.isEmpty()) {
            sender.sendMessage("§eNo stored portals found for: §7" + String.join("§8, §7", untouched));
        }

        if (cleared.isEmpty() && untouched.isEmpty()) {
            sender.sendMessage("§eNo portal data was changed for §f" + target.getName() + "§e.");
        }
    }

    private Set<String> collectGunIds(Player player) {
        Set<String> ids = new HashSet<>();
        PlayerInventory inventory = player.getInventory();
        collectFromItems(ids, inventory.getContents());
        collectFromItems(ids, inventory.getArmorContents());
        collectFromItems(ids, inventory.getExtraContents());
        collectFromItem(ids, inventory.getItemInOffHand());
        collectFromItems(ids, player.getEnderChest().getContents());
        return ids;
    }

    private void collectFromItems(Set<String> ids, ItemStack[] items) {
        if (items == null) {
            return;
        }
        for (ItemStack item : items) {
            collectFromItem(ids, item);
        }
    }

    private void collectFromItem(Set<String> ids, ItemStack item) {
        String gunId = ItemHandler.isValidGun(item);
        if (gunId != null) {
            ids.add(gunId);
        }
    }

    private boolean removeGunData(String gunId) {
        boolean modified = false;

        Portal primary = ActivePortalsHandler.getPrimaryPortal(gunId);
        if (primary != null) {
            primary.delete();
            ActivePortalsHandler.removePrimaryPortal(gunId);
            modified = true;
        }

        Portal secondary = ActivePortalsHandler.getSecondaryPortal(gunId);
        if (secondary != null) {
            secondary.delete();
            ActivePortalsHandler.removeSecondaryPortal(gunId);
            modified = true;
        }

        String encodedId = ItemHandler.saveable(gunId);
        if (PersitentHandler.exists("porticleguns." + encodedId)) {
            PersitentHandler.set("porticleguns." + encodedId, null);
            PersitentHandler.saveAll();
            modified = true;
        }

        return modified;
    }

    private void handleReload(CommandSender sender) {
        PorticleGun.getInstance().reloadConfig();
        LoadingHandler.getInstance().reload();
        sender.sendMessage("§aPorticleGun data reloaded from disk.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if (!hasAdminSuggestions(sender)) {
                return Collections.emptyList();
            }
            List<String> options = Arrays.asList("list", "remove", "clearplayer", "reload");
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return options.stream()
                    .filter(option -> option.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && hasAdminSuggestions(sender)) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("remove")) {
                if (!PersitentHandler.exists("porticleguns")) {
                    return Collections.emptyList();
                }
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return PersitentHandler.getSection("porticleguns").stream()
                        .map(ItemHandler::useable)
                        .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .collect(Collectors.toList());
            }
            if (sub.equals("clearplayer")) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }

    private boolean hasAdminSuggestions(CommandSender sender) {
        return !(sender instanceof Player) || ((Player) sender).hasPermission(ADMIN_PERMISSION);
    }

    @EventHandler
    public void on(InventoryClickEvent event) {
        if (event.getView().getTitle().toString().equals(TITLE)) {
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
        if (event.getView().getTitle().toString().equals(TITLE)) {
            //SoundHandler.playSound(event.getPlayer().getEyeLocation(), APGSound.INV_CLOSE);
        }
    }
}
