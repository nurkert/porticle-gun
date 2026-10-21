package eu.nurkert.porticlegun.commands.sub;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.commands.SubCommand;
import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.rift.RiftItems;
import eu.nurkert.porticlegun.rift.RiftModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /porticlegun give <item> [player] [amount]}: hands out any PorticleGun item.
 */
public class GiveCommand implements SubCommand {

    private static final List<String> ITEMS =
            List.of("porticlegun", "interdimensional_gun", "rift_shard", "portal_fluid");

    @Override
    public String getName() {
        return "give";
    }

    @Override
    public boolean isAdminOnly() {
        return true;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1 || !ITEMS.contains(args[0].toLowerCase(Locale.ROOT))) {
            sender.sendMessage(MessageManager.getMessage(
                    sender, "commands.usage.give", Map.of("label", label, "items", String.join(", ", ITEMS))));
            return;
        }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
        } else {
            target = sender instanceof Player player ? player : null;
        }
        if (target == null) {
            sender.sendMessage(MessageManager.getMessage(
                    sender, "commands.give.no-player", Map.of("player", args.length >= 2 ? args[1] : "-")));
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException exception) {
                sender.sendMessage(MessageManager.getMessage(
                        sender, "commands.usage.give", Map.of("label", label, "items", String.join(", ", ITEMS))));
                return;
            }
        }
        String item = args[0].toLowerCase(Locale.ROOT);
        List<ItemStack> stacks = new ArrayList<>();
        if (item.equals("porticlegun")) {
            for (int i = 0; i < amount; i++) {
                stacks.add(ItemHandler.generateNewGun());
            }
        } else {
            RiftModule rift = PorticleGun.getInstance().getRiftModule();
            if (rift == null) {
                sender.sendMessage(MessageManager.getMessage(sender, "commands.give.rift-disabled"));
                return;
            }
            RiftItems.Type type = RiftItems.Type.valueOf(item.toUpperCase(Locale.ROOT));
            if (type == RiftItems.Type.INTERDIMENSIONAL_GUN) {
                for (int i = 0; i < amount; i++) {
                    stacks.add(RiftItems.create(type, 1, rift.settings()));
                }
            } else {
                stacks.add(RiftItems.create(type, amount, rift.settings()));
            }
        }
        for (ItemStack leftover :
                target.getInventory().addItem(stacks.toArray(new ItemStack[0])).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
        sender.sendMessage(MessageManager.getMessage(
                sender,
                "commands.give.success",
                Map.of("item", item, "amount", String.valueOf(amount), "player", target.getName())));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return ITEMS.stream().filter(item -> item.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
