package eu.nurkert.porticlegun.commands;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.builders.ItemBuilder;
import eu.nurkert.porticlegun.handlers.LoadingHandler;
import eu.nurkert.porticlegun.handlers.PersitentHandler;
import eu.nurkert.porticlegun.handlers.item.ItemHandler;
import eu.nurkert.porticlegun.handlers.portals.ActivePortalsHandler;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.portals.Portal;
import eu.nurkert.porticlegun.util.PluginJarRenamer;
import eu.nurkert.porticlegun.util.PluginJarRenamer.RenameResult;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

public class PorticleGunCommand implements CommandExecutor, Listener, TabCompleter {

    private static final String COMMAND_PERMISSION = "porticlegun.command";
    private static final String ADMIN_PERMISSION = "porticlegun.admin";
    private static final int QUICK_RENAME_SLOT = 0;
    private static final List<String> ADMIN_SUBCOMMANDS = Arrays.asList("list", "remove", "clearplayer", "reload");

    public PorticleGunCommand() {
    }

    public void reloadMessages() {
        // Inventory titles are resolved dynamically when the menu is opened.
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                if (player.hasPermission(COMMAND_PERMISSION)) {
                    player.openInventory(createMenu(player));
                    //SoundHandler.playSound(player, APGSound.INV_OPEN);
                } else {
                    player.sendMessage(MessageManager.getMessage(player, "commands.general.no-permission"));
                }
            } else {
                sender.sendMessage(MessageManager.getMessage(sender, "commands.general.players-only"));
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
                    sender.sendMessage(MessageManager.getMessage(sender, "commands.usage.remove", Map.of("label", label)));
                    return true;
                }
                handleRemove(sender, args[1]);
                return true;
            case "clearplayer":
                if (!ensureAdmin(sender)) return true;
                if (args.length < 2) {
                    sender.sendMessage(MessageManager.getMessage(sender, "commands.usage.clearplayer", Map.of("label", label)));
                    return true;
                }
                handleClearPlayer(sender, args[1]);
                return true;
            case "reload":
                if (!ensureAdmin(sender)) return true;
                handleReload(sender);
                return true;
            default:
                sender.sendMessage(MessageManager.getMessage(sender, "commands.general.unknown", Map.of(
                        "subcommands", String.join(", ", ADMIN_SUBCOMMANDS))));
                return true;
        }
    }

    private Inventory createMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, InventoryType.DROPPER,
                MessageManager.getMessage(player, "menus.porticlegun.title"));
        ItemStack filler = createFiller(player);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i == 4) {
                continue;
            }
            inventory.setItem(i, filler.clone());
        }
        ItemStack renameItem = createQuickRenameItem(player);
        if (renameItem != null) {
            inventory.setItem(QUICK_RENAME_SLOT, renameItem);
        }
        inventory.setItem(4, ItemHandler.generateNewGun());
        return inventory;
    }

    private ItemStack createFiller(Player player) {
        return new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
                .setName(MessageManager.getMessage(player, "menus.porticlegun.filler-name"))
                .build();
    }

    private ItemStack createQuickRenameItem(Player player) {
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            return null;
        }
        if (!PluginJarRenamer.isRenameAvailable(PorticleGun.getInstance())) {
            return null;
        }

        String target = PluginJarRenamer.getTargetFileName(PorticleGun.getInstance());
        String name = MessageManager.getMessage(player, "menus.porticlegun.rename-jar-name",
                Map.of("target", target));
        String loreRaw = MessageManager.getMessage(player, "menus.porticlegun.rename-jar-lore",
                Map.of("target", target));

        ItemBuilder builder = new ItemBuilder(Material.NAME_TAG)
                .setName(name);
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

    private boolean ensureAdmin(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return true;
        }
        if (((Player) sender).hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        sender.sendMessage(MessageManager.getMessage(sender, "commands.admin.no-permission"));
        return false;
    }

    private void handleList(CommandSender sender) {
        if (!PersitentHandler.exists("porticleguns")) {
            sender.sendMessage(MessageManager.getMessage(sender, "commands.list.empty"));
            return;
        }

        List<String> entries = PersitentHandler.getSection("porticleguns");
        if (entries.isEmpty()) {
            sender.sendMessage(MessageManager.getMessage(sender, "commands.list.empty"));
            return;
        }

        sender.sendMessage(MessageManager.getMessage(sender, "commands.list.header",
                Map.of("count", String.valueOf(entries.size()))));
        for (String entry : entries) {
            String gunId = ItemHandler.useable(entry);
            if (gunId == null) {
                PorticleGun.getInstance().getLogger().warning("Skipping invalid persisted gun id '" + entry + "' while listing porticle guns.");
                continue;
            }
            String basePath = "porticleguns." + entry;
            boolean persistedPrimary = PersitentHandler.exists(basePath + ".primary.position");
            boolean persistedSecondary = PersitentHandler.exists(basePath + ".secondary.position");
            boolean activePrimary = ActivePortalsHandler.hasPrimaryPortal(gunId);
            boolean activeSecondary = ActivePortalsHandler.hasSecondaryPortal(gunId);

            String primaryStatus = buildStatus(sender, activePrimary, persistedPrimary);
            String secondaryStatus = buildStatus(sender, activeSecondary, persistedSecondary);

            sender.sendMessage(MessageManager.getMessage(sender, "commands.list.entry", Map.of(
                    "gun_id", gunId,
                    "primary_status", primaryStatus,
                    "secondary_status", secondaryStatus
            )));
        }
    }

    private String buildStatus(CommandSender sender, boolean active, boolean persisted) {
        if (active) {
            return MessageManager.getMessage(sender, "commands.list.status.active");
        }
        if (persisted) {
            return MessageManager.getMessage(sender, "commands.list.status.saved");
        }
        return MessageManager.getMessage(sender, "commands.list.status.none");
    }

    private void handleRemove(CommandSender sender, String gunId) {
        if (!ItemHandler.isValidGunID(gunId)) {
            sender.sendMessage(MessageManager.getMessage(sender, "commands.remove.invalid-id", Map.of("gun_id", gunId)));
            return;
        }

        if (removeGunData(gunId)) {
            sender.sendMessage(MessageManager.getMessage(sender, "commands.remove.success", Map.of("gun_id", gunId)));
        } else {
            sender.sendMessage(MessageManager.getMessage(sender, "commands.remove.not-found", Map.of("gun_id", gunId)));
        }
    }

    private void handleClearPlayer(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(MessageManager.getMessage(sender, "commands.clearplayer.not-online", Map.of("player", playerName)));
            return;
        }

        Set<String> gunIds = collectGunIds(target);
        if (gunIds.isEmpty()) {
            sender.sendMessage(MessageManager.getMessage(sender, "commands.clearplayer.none", Map.of("player", target.getName())));
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
            sender.sendMessage(MessageManager.getMessage(sender, "commands.clearplayer.cleared", Map.of(
                    "player", target.getName(),
                    "gun_ids", formatIdList(sender, cleared)
            )));
        }

        if (!untouched.isEmpty()) {
            sender.sendMessage(MessageManager.getMessage(sender, "commands.clearplayer.untouched", Map.of(
                    "gun_ids", formatIdList(sender, untouched)
            )));
        }

        if (cleared.isEmpty() && untouched.isEmpty()) {
            sender.sendMessage(MessageManager.getMessage(sender, "commands.clearplayer.no-change", Map.of(
                    "player", target.getName()
            )));
        }
    }

    private String formatIdList(CommandSender sender, List<String> ids) {
        String separator = MessageManager.getMessage(sender, "lists.default-separator");
        return ids.stream()
                .map(id -> MessageManager.getMessage(sender, "commands.clearplayer.id-format", Map.of("gun_id", id)))
                .collect(Collectors.joining(separator));
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
        sender.sendMessage(MessageManager.getMessage(sender, "commands.reload.success"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if (!hasAdminSuggestions(sender)) {
                return Collections.emptyList();
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return ADMIN_SUBCOMMANDS.stream()
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
                        .filter(Objects::nonNull)
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
        if (MessageManager.matchesConfiguredValue("menus.porticlegun.title", event.getView().getTitle())) {
            if (event.getRawSlot() < event.getInventory().getSize()) {
                event.setCancelled(true);
                if (event.getRawSlot() == 4) {
                    event.setCursor(ItemHandler.generateNewGun());
                } else if (event.getRawSlot() == QUICK_RENAME_SLOT && event.getWhoClicked() instanceof Player) {
                    handleQuickRename((Player) event.getWhoClicked(), event.getInventory());
                }
            }
        }
    }

    private void handleQuickRename(Player player, Inventory inventory) {
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(MessageManager.getMessage(player, "commands.admin.no-permission"));
            return;
        }

        PorticleGun plugin = PorticleGun.getInstance();
        String target = PluginJarRenamer.getTargetFileName(plugin);
        Map<String, String> placeholders = Map.of("target", target);
        RenameResult result = PluginJarRenamer.renameToDataFolder(plugin);

        switch (result) {
            case SUCCESS:
                player.sendMessage(MessageManager.getMessage(player, "menus.porticlegun.rename-jar-success", placeholders));
                player.closeInventory();
                break;
            case ALREADY_MATCHED:
                player.sendMessage(MessageManager.getMessage(player, "menus.porticlegun.rename-jar-already", placeholders));
                updateRenameSlot(inventory, player);
                break;
            case CONFLICT:
                player.sendMessage(MessageManager.getMessage(player, "menus.porticlegun.rename-jar-conflict", placeholders));
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

    @EventHandler
    public void on(InventoryCloseEvent event) {
        if (MessageManager.matchesConfiguredValue("menus.porticlegun.title", event.getView().getTitle())) {
            //SoundHandler.playSound(event.getPlayer().getEyeLocation(), APGSound.INV_CLOSE);
        }
    }
}
