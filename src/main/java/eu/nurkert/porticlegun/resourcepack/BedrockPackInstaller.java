package eu.nurkert.porticlegun.resourcepack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Copies the Bedrock resource pack and the Geyser item mapping of the 3D gun into Geyser's folders.
 *
 * <p>PorticleGun loads before Geyser ({@code loadbefore} in plugin.yml) and installs the files in {@code onLoad}, so
 * Geyser picks them up on the same start. When Geyser runs on a proxy instead, the files have to be copied there by
 * hand; they are also attached to every GitHub release.
 */
public final class BedrockPackInstaller {

    private static final String GEYSER_FOLDER = "Geyser-Spigot";
    private static final String PACK_RESOURCE = "bedrock/porticlegun.mcpack";
    private static final String MAPPINGS_RESOURCE = "bedrock/porticlegun_mappings.json";

    private BedrockPackInstaller() {}

    /**
     * @return whether a file was added or updated
     */
    public static boolean install(JavaPlugin plugin) {
        File geyserFolder = new File(plugin.getDataFolder().getParentFile(), GEYSER_FOLDER);
        if (!geyserFolder.isDirectory() && !isGeyserInstalled(plugin)) {
            return false;
        }

        try {
            boolean changed = copy(plugin, PACK_RESOURCE, geyserFolder.toPath().resolve("packs/porticlegun.mcpack"));
            changed |= copy(
                    plugin,
                    MAPPINGS_RESOURCE,
                    geyserFolder.toPath().resolve("custom_mappings/porticlegun_mappings.json"));
            if (changed) {
                plugin.getLogger().info("Installed the PorticleGun Bedrock pack and item mapping for Geyser.");
            }
            return changed;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not install the Bedrock pack for Geyser", exception);
            return false;
        }
    }

    /**
     * Warns if Geyser is configured to ignore custom items, which the 3D gun needs.
     */
    public static void checkGeyserConfig(JavaPlugin plugin) {
        File config = new File(new File(plugin.getDataFolder().getParentFile(), GEYSER_FOLDER), "config.yml");
        if (!config.isFile()) {
            return;
        }
        YamlConfiguration geyserConfig = YamlConfiguration.loadConfiguration(config);
        if (geyserConfig.contains("enable-custom-content") && !geyserConfig.getBoolean("enable-custom-content")) {
            Logger logger = plugin.getLogger();
            logger.warning("Geyser has enable-custom-content set to false, so Bedrock players see the gun as a "
                    + "brewing stand. Set it to true in plugins/" + GEYSER_FOLDER + "/config.yml and restart.");
        }
    }

    private static boolean isGeyserInstalled(JavaPlugin plugin) {
        File[] jars = plugin.getDataFolder().getParentFile().listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars == null) {
            return false;
        }
        return Arrays.stream(jars).anyMatch(jar -> {
            try {
                return GEYSER_FOLDER.equals(
                        plugin.getPluginLoader().getPluginDescription(jar).getName());
            } catch (Exception exception) {
                return false;
            }
        });
    }

    private static boolean copy(JavaPlugin plugin, String resource, Path target) throws IOException {
        byte[] content;
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                throw new IOException("missing " + resource + " in the plugin jar");
            }
            content = in.readAllBytes();
        }
        if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), content)) {
            return false;
        }
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temp, content);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }
}
