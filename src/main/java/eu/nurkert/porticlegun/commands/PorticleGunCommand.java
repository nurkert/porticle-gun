package eu.nurkert.porticlegun.commands;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.commands.sub.ClearPlayerCommand;
import eu.nurkert.porticlegun.commands.sub.GiveCommand;
import eu.nurkert.porticlegun.commands.sub.ListCommand;
import eu.nurkert.porticlegun.commands.sub.ReloadCommand;
import eu.nurkert.porticlegun.commands.sub.RemoveCommand;
import eu.nurkert.porticlegun.commands.sub.RemovePortalCommand;
import eu.nurkert.porticlegun.messages.MessageManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /porticlegun}: opens the {@link GunMenu} without arguments, otherwise dispatches to a {@link SubCommand}.
 */
public class PorticleGunCommand implements CommandExecutor, TabCompleter {

    private final GunMenu menu = new GunMenu();
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    public PorticleGunCommand(PorticleGun plugin) {
        GunData gunData = new GunData(plugin.getActivePortals(), plugin.getPersistence());
        register(new RemovePortalCommand(plugin.getActivePortals(), plugin.getConfigManager()));
        register(new ListCommand(plugin.getActivePortals(), plugin.getPersistence()));
        register(new RemoveCommand(gunData, plugin.getPersistence()));
        register(new ClearPlayerCommand(gunData));
        register(new ReloadCommand(plugin));
        register(new GiveCommand());
    }

    private void register(SubCommand subCommand) {
        subCommands.put(subCommand.getName(), subCommand);
    }

    public GunMenu getMenu() {
        return menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageManager.getMessage(sender, "commands.general.players-only"));
            } else if (!player.hasPermission(Permissions.COMMAND)) {
                player.sendMessage(MessageManager.getMessage(player, "commands.general.no-permission"));
            } else {
                menu.open(player);
            }
            return true;
        }

        SubCommand subCommand = subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subCommand == null) {
            sender.sendMessage(MessageManager.getMessage(
                    sender,
                    "commands.general.unknown",
                    Map.of("subcommands", String.join(", ", getAvailableNames(sender)))));
            return true;
        }

        if (subCommand.isAdminOnly()) {
            if (!Permissions.isAdmin(sender)) {
                sender.sendMessage(MessageManager.getMessage(sender, "commands.admin.no-permission"));
                return true;
            }
        } else if (sender instanceof Player && !sender.hasPermission(Permissions.COMMAND)) {
            sender.sendMessage(MessageManager.getMessage(sender, "commands.general.no-permission"));
            return true;
        }

        subCommand.execute(sender, label, Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return getAvailableNames(sender).stream()
                    .filter(name -> name.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        SubCommand subCommand = subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subCommand == null || !canSuggest(sender, subCommand)) {
            return Collections.emptyList();
        }
        return subCommand.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    private List<String> getAvailableNames(CommandSender sender) {
        return subCommands.values().stream()
                .filter(subCommand -> canSuggest(sender, subCommand))
                .map(SubCommand::getName)
                .collect(Collectors.toList());
    }

    private boolean canSuggest(CommandSender sender, SubCommand subCommand) {
        return subCommand.isAdminOnly() ? Permissions.isAdmin(sender) : Permissions.canUseGeneral(sender);
    }
}
