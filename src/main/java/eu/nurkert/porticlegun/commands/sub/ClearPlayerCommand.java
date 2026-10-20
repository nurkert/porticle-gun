package eu.nurkert.porticlegun.commands.sub;

import eu.nurkert.porticlegun.commands.GunData;
import eu.nurkert.porticlegun.commands.SubCommand;
import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.messages.MessageManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * {@code /porticlegun clearplayer <player>}: removes the data of every gun an online player carries.
 */
public class ClearPlayerCommand implements SubCommand {

    @Override
    public String getName() {
        return "clearplayer";
    }

    @Override
    public boolean isAdminOnly() {
        return true;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(MessageManager.getMessage(sender, "commands.usage.clearplayer", Map.of("label", label)));
            return;
        }

        String playerName = args[0];
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(
                    MessageManager.getMessage(sender, "commands.clearplayer.not-online", Map.of("player", playerName)));
            return;
        }

        Set<String> gunIds = collectGunIds(target);
        if (gunIds.isEmpty()) {
            sender.sendMessage(
                    MessageManager.getMessage(sender, "commands.clearplayer.none", Map.of("player", target.getName())));
            return;
        }

        List<String> cleared = new ArrayList<>();
        List<String> untouched = new ArrayList<>();

        for (String gunId : gunIds) {
            if (GunData.remove(gunId)) {
                cleared.add(gunId);
            } else {
                untouched.add(gunId);
            }
        }

        if (!cleared.isEmpty()) {
            sender.sendMessage(MessageManager.getMessage(
                    sender,
                    "commands.clearplayer.cleared",
                    Map.of(
                            "player", target.getName(),
                            "gun_ids", formatIdList(sender, cleared))));
        }

        if (!untouched.isEmpty()) {
            sender.sendMessage(MessageManager.getMessage(
                    sender, "commands.clearplayer.untouched", Map.of("gun_ids", formatIdList(sender, untouched))));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .collect(Collectors.toList());
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
}
