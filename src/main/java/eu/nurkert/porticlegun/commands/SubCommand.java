package eu.nurkert.porticlegun.commands;

import java.util.Collections;
import java.util.List;
import org.bukkit.command.CommandSender;

/**
 * A sub command of {@code /porticlegun}, e.g. {@code /porticlegun list}.
 */
public interface SubCommand {

    /**
     * @return the name typed after {@code /porticlegun}, in lower case
     */
    String getName();

    /**
     * @return whether only admins ({@link Permissions#ADMIN}) may use it; otherwise players need
     *     {@link Permissions#COMMAND}
     */
    boolean isAdminOnly();

    /**
     * @param args the arguments after the sub command name
     */
    void execute(CommandSender sender, String label, String[] args);

    /**
     * @param args the arguments after the sub command name, the last one being completed
     */
    default List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
