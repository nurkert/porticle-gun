package eu.nurkert.porticlegun.commands;

import eu.nurkert.porticlegun.builders.ItemBuilder;
import eu.nurkert.porticlegun.config.ConfigManager;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PorticleGunCommand implements CommandExecutor, Listener, TabCompleter {


    private static final String TITLE = "§8PorticleGun";
    private static final String COMMAND_PERMISSION = "porticlegun.command";
    private static final String ADMIN_PERMISSION = "porticlegun.admin";

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
        if (args.length > 0) {
            String subCommand = args[0].toLowerCase(Locale.ROOT);
            switch (subCommand) {
                case "reload":
                    return handleReload(sender);
                case "list":
                    return handleList(sender);
                case "remove":
                    return handleRemove(sender, args);
                case "clearplayer":
                    return handleClearPlayer(sender, args);
                default:
                    sender.sendMessage("§cUnbekannter Unterbefehl.");
                    return true;
            }
        }

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

    private boolean handleReload(CommandSender sender) {
        if (!checkAdminPermission(sender)) {
            return true;
        }

        ConfigManager.reload();
        sender.sendMessage("§aDie PorticleGun-Konfiguration wurde neu geladen.");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!checkAdminPermission(sender)) {
            return true;
        }

        if (!PersitentHandler.exists("porticleguns")) {
            sender.sendMessage("§7Es sind derzeit keine Portale gespeichert.");
            return true;
        }

        List<String> storedIds = PersitentHandler.getSection("porticleguns");
        if (storedIds.isEmpty()) {
            sender.sendMessage("§7Es sind derzeit keine Portale gespeichert.");
            return true;
        }

        sender.sendMessage("§8§m------------------------------");
        sender.sendMessage("§5PorticleGun §7- Gespeicherte Portale:");
        for (String storedId : storedIds) {
            String usableId;
            try {
                usableId = ItemHandler.useable(storedId);
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(" §cUnbekannte oder beschädigte ID: §7" + storedId);
                continue;
            }

            boolean hasPrimary = PersitentHandler.exists("porticleguns." + storedId + ".primary.position");
            boolean hasSecondary = PersitentHandler.exists("porticleguns." + storedId + ".secondary.position");
            sender.sendMessage(String.format(" §7- §d%s §8(§7Speicher: %s§8) §7P:%s §7S:%s",
                    usableId,
                    storedId,
                    formatStatus(hasPrimary),
                    formatStatus(hasSecondary)));
        }
        sender.sendMessage("§8§m------------------------------");
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!checkAdminPermission(sender)) {
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cBitte gib die ID an, die entfernt werden soll: /porticlegun remove <id>");
            return true;
        }

        String storageId = resolveStorageId(args[1]);
        if (storageId == null) {
            sender.sendMessage("§cDie angegebene ID konnte nicht gefunden werden.");
            return true;
        }

        String usableId;
        try {
            usableId = ItemHandler.useable(storageId);
        } catch (IllegalArgumentException exception) {
            usableId = args[1];
        }

        if (removePortalData(storageId, usableId)) {
            sender.sendMessage("§aAlle gespeicherten Daten für §d" + usableId + " §awurden entfernt.");
        } else {
            sender.sendMessage("§7Für die angegebene ID waren keine Portale aktiv oder gespeichert.");
        }
        return true;
    }

    private boolean handleClearPlayer(CommandSender sender, String[] args) {
        if (!checkAdminPermission(sender)) {
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cBitte gib einen Spielernamen an: /porticlegun clearplayer <name>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cDer Spieler '" + args[1] + "' ist nicht online.");
            return true;
        }

        Set<String> gunIds = collectGunIds(target);
        if (gunIds.isEmpty()) {
            sender.sendMessage("§7Der Spieler besitzt derzeit keine PorticleGuns.");
            return true;
        }

        int removed = 0;
        for (String gunId : gunIds) {
            String storageId;
            try {
                storageId = ItemHandler.saveable(gunId);
            } catch (IllegalArgumentException exception) {
                continue;
            }

            if (removePortalData(storageId, gunId)) {
                removed++;
            }
        }

        sender.sendMessage("§aFür §d" + target.getName() + " §awurden " + removed + " PorticleGun-Datensätze zurückgesetzt.");
        if (sender != target) {
            target.sendMessage("§eDeine gespeicherten Portale wurden von einem Administrator gelöscht.");
        }
        return true;
    }

    private boolean checkAdminPermission(CommandSender sender) {
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        sender.sendMessage("§cDu hast keine Berechtigung für diesen Verwaltungsbefehl.");
        return false;
    }

    private Set<String> collectGunIds(Player player) {
        Set<String> ids = new HashSet<>();
        PlayerInventory inventory = player.getInventory();
        addGunIds(ids, inventory.getContents());
        addGunIds(ids, inventory.getArmorContents());
        addGunIds(ids, inventory.getExtraContents());
        addGunIds(ids, player.getEnderChest().getContents());
        return ids;
    }

    private void addGunIds(Collection<String> ids, ItemStack[] stacks) {
        if (stacks == null) {
            return;
        }
        for (ItemStack stack : stacks) {
            String gunId = ItemHandler.isValidGun(stack);
            if (gunId != null) {
                ids.add(gunId);
            }
        }
    }

    private boolean removePortalData(String storageId, String usableId) {
        boolean removed = false;

        Portal primary = ActivePortalsHandler.getPrimaryPortal(usableId);
        if (primary != null) {
            primary.delete();
            ActivePortalsHandler.removePrimaryPortal(usableId);
            removed = true;
        }

        Portal secondary = ActivePortalsHandler.getSecondaryPortal(usableId);
        if (secondary != null) {
            secondary.delete();
            ActivePortalsHandler.removeSecondaryPortal(usableId);
            removed = true;
        }

        if (PersitentHandler.exists("porticleguns." + storageId)) {
            PersitentHandler.set("porticleguns." + storageId, null);
            removed = true;
        }

        return removed;
    }

    private String resolveStorageId(String rawId) {
        if (PersitentHandler.exists("porticleguns." + rawId)) {
            return rawId;
        }
        try {
            String converted = ItemHandler.saveable(rawId);
            if (PersitentHandler.exists("porticleguns." + converted)) {
                return converted;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    private String formatStatus(boolean status) {
        return status ? "§a✔" : "§c✘";
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!"porticlegun".equalsIgnoreCase(command.getName())) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                options.add("list");
                options.add("remove");
                options.add("clearplayer");
                options.add("reload");
            }
            return filterStartingWith(args[0], options);
        }

        if (args.length == 2 && sender.hasPermission(ADMIN_PERMISSION)) {
            if ("remove".equalsIgnoreCase(args[0])) {
                List<String> storedIds = PersitentHandler.getSection("porticleguns");
                return filterStartingWith(args[1], storedIds);
            }
            if ("clearplayer".equalsIgnoreCase(args[0])) {
                Collection<? extends Player> players = Bukkit.getOnlinePlayers();
                List<String> names = new ArrayList<>(players.size());
                for (Player player : players) {
                    names.add(player.getName());
                }
                return filterStartingWith(args[1], names);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterStartingWith(String input, List<String> options) {
        if (options == null || options.isEmpty()) {
            return Collections.emptyList();
        }
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();
        for (String option : options) {
            if (option == null) {
                continue;
            }
            if (prefix.isEmpty() || option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                completions.add(option);
            }
        }
        return completions;
    }
}
