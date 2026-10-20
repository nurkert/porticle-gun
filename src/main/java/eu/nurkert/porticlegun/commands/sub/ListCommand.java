package eu.nurkert.porticlegun.commands.sub;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.commands.SubCommand;
import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.persistence.PersistentHandler;
import eu.nurkert.porticlegun.portals.ActivePortalsHandler;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;

public class ListCommand implements SubCommand {

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public boolean isAdminOnly() {
        return true;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!PersistentHandler.exists("porticleguns")) {
            sender.sendMessage(MessageManager.getMessage(sender, "commands.list.empty"));
            return;
        }

        List<String> entries = PersistentHandler.getSection("porticleguns");
        if (entries.isEmpty()) {
            sender.sendMessage(MessageManager.getMessage(sender, "commands.list.empty"));
            return;
        }

        sender.sendMessage(MessageManager.getMessage(
                sender, "commands.list.header", Map.of("count", String.valueOf(entries.size()))));
        for (String entry : entries) {
            String gunId = ItemHandler.useable(entry);
            if (gunId == null) {
                PorticleGun.getInstance()
                        .getLogger()
                        .warning("Skipping invalid persisted gun id '" + entry + "' while listing porticle guns.");
                continue;
            }
            String basePath = "porticleguns." + entry;
            boolean persistedPrimary = PersistentHandler.exists(basePath + ".primary.position");
            boolean persistedSecondary = PersistentHandler.exists(basePath + ".secondary.position");
            boolean activePrimary = ActivePortalsHandler.hasPrimaryPortal(gunId);
            boolean activeSecondary = ActivePortalsHandler.hasSecondaryPortal(gunId);

            String primaryStatus = buildStatus(sender, activePrimary, persistedPrimary);
            String secondaryStatus = buildStatus(sender, activeSecondary, persistedSecondary);

            sender.sendMessage(MessageManager.getMessage(
                    sender,
                    "commands.list.entry",
                    Map.of(
                            "gun_id", gunId,
                            "primary_status", primaryStatus,
                            "secondary_status", secondaryStatus)));
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
}
