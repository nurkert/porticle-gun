package eu.nurkert.porticlegun.commands.sub;

import eu.nurkert.porticlegun.commands.GunData;
import eu.nurkert.porticlegun.commands.SubCommand;
import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.persistence.PersistentHandler;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.bukkit.command.CommandSender;

/**
 * {@code /porticlegun remove <id>}: closes the portals of a gun and deletes its data.
 */
public class RemoveCommand implements SubCommand {

    private final GunData gunData;
    private final PersistentHandler persistence;

    public RemoveCommand(GunData gunData, PersistentHandler persistence) {
        this.gunData = gunData;
        this.persistence = persistence;
    }

    @Override
    public String getName() {
        return "remove";
    }

    @Override
    public boolean isAdminOnly() {
        return true;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(MessageManager.getMessage(sender, "commands.usage.remove", Map.of("label", label)));
            return;
        }

        String gunId = args[0];
        if (!ItemHandler.isValidGunID(gunId)) {
            sender.sendMessage(
                    MessageManager.getMessage(sender, "commands.remove.invalid-id", Map.of("gun_id", gunId)));
            return;
        }

        if (gunData.remove(gunId)) {
            sender.sendMessage(MessageManager.getMessage(sender, "commands.remove.success", Map.of("gun_id", gunId)));
        } else {
            sender.sendMessage(MessageManager.getMessage(sender, "commands.remove.not-found", Map.of("gun_id", gunId)));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1 || !persistence.exists("porticleguns")) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return persistence.getSection("porticleguns").stream()
                .map(ItemHandler::useable)
                .filter(Objects::nonNull)
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                .collect(Collectors.toList());
    }
}
