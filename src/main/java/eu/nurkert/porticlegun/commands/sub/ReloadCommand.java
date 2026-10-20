package eu.nurkert.porticlegun.commands.sub;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.commands.SubCommand;
import eu.nurkert.porticlegun.messages.MessageManager;
import org.bukkit.command.CommandSender;

/**
 * {@code /porticlegun reload}: reloads configuration, messages and stored portals.
 */
public class ReloadCommand implements SubCommand {

    private final PorticleGun plugin;

    public ReloadCommand(PorticleGun plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public boolean isAdminOnly() {
        return true;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        plugin.reloadConfig();
        plugin.getLoadingHandler().reload();
        sender.sendMessage(MessageManager.getMessage(sender, "commands.reload.success"));
    }
}
