package eu.nurkert.porticlegun.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Utility methods for working with the plugin's jar file.
 */
public final class PluginJarRenamer {

    private PluginJarRenamer() {
    }

    public enum RenameResult {
        SUCCESS,
        ALREADY_MATCHED,
        CONFLICT,
        UNAVAILABLE,
        ERROR
    }

    private static Optional<Path> resolvePluginJar(JavaPlugin plugin) {
        if (plugin == null) {
            return Optional.empty();
        }

        try {
            CodeSource source = plugin.getClass().getProtectionDomain().getCodeSource();
            if (source == null) {
                return Optional.empty();
            }
            URL location = source.getLocation();
            if (location == null) {
                return Optional.empty();
            }
            Path path = Paths.get(location.toURI());
            if (!Files.isRegularFile(path)) {
                return Optional.empty();
            }
            return Optional.of(path);
        } catch (URISyntaxException | IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Unable to resolve plugin jar location.", ex);
            return Optional.empty();
        }
    }

    public static boolean isRenameAvailable(JavaPlugin plugin) {
        Optional<Path> current = resolvePluginJar(plugin);
        if (current.isEmpty()) {
            return false;
        }
        Path desired = current.get().resolveSibling(getTargetFileName(plugin));
        return !current.get().getFileName().equals(desired.getFileName());
    }

    public static String getTargetFileName(JavaPlugin plugin) {
        String folderName = plugin.getDataFolder().getName();
        return folderName + ".jar";
    }

    public static Optional<String> getCurrentFileName(JavaPlugin plugin) {
        return resolvePluginJar(plugin).map(path -> path.getFileName().toString());
    }

    public static RenameResult renameToDataFolder(JavaPlugin plugin) {
        Optional<Path> currentPath = resolvePluginJar(plugin);
        if (currentPath.isEmpty()) {
            return RenameResult.UNAVAILABLE;
        }

        Path current = currentPath.get();
        Path target = current.resolveSibling(getTargetFileName(plugin));

        if (current.getFileName().equals(target.getFileName())) {
            return RenameResult.ALREADY_MATCHED;
        }

        if (Files.exists(target)) {
            return RenameResult.CONFLICT;
        }

        try {
            try {
                Files.move(current, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(current, target);
            }
            return RenameResult.SUCCESS;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to rename plugin jar.", ex);
            return RenameResult.ERROR;
        }
    }
}

