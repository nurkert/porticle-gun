package eu.nurkert.porticlegun.resourcepack;

import eu.nurkert.porticlegun.util.BedrockSupport;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
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
     * Warns about Geyser settings that would hide the 3D gun from Bedrock players, and explains what to do when Geyser
     * runs on a proxy instead of this server.
     */
    public static void checkGeyserSetup(JavaPlugin plugin) {
        File geyserFolder = new File(plugin.getDataFolder().getParentFile(), GEYSER_FOLDER);
        if (!geyserFolder.isDirectory()) {
            if (BedrockSupport.isEnabled()) {
                exportForProxy(plugin);
            }
            return;
        }

        File config = new File(geyserFolder, "config.yml");
        if (!config.isFile()) {
            return;
        }
        YamlConfiguration geyserConfig = YamlConfiguration.loadConfiguration(config);
        Logger logger = plugin.getLogger();
        if (Boolean.FALSE.equals(findBoolean(geyserConfig, "enable-custom-content"))) {
            logger.warning("Geyser has enable-custom-content set to false, so Bedrock players see the gun as a "
                    + "brewing stand. Set it to true in plugins/" + GEYSER_FOLDER + "/config.yml and restart.");
        }
        if (Boolean.FALSE.equals(findBoolean(geyserConfig, "force-resource-packs"))) {
            logger.warning("Geyser has force-resource-packs set to false: Bedrock players who decline the resource "
                    + "pack cannot see the gun. Set it to true in plugins/" + GEYSER_FOLDER + "/config.yml.");
        }
    }

    /**
     * Finds a setting at the top level (older Geyser configs) or in any section (e.g. {@code gameplay} since Geyser
     * 2.9).
     *
     * @return the value, or {@code null} if the setting is missing
     */
    static Boolean findBoolean(ConfigurationSection config, String key) {
        for (String path : config.getKeys(true)) {
            if ((path.equals(key) || path.endsWith("." + key)) && config.isBoolean(path)) {
                return config.getBoolean(path);
            }
        }
        return null;
    }

    /**
     * Floodgate is installed but Geyser is not: Geyser most likely runs on the proxy, which this plugin cannot reach.
     * Puts both files into the plugin folder and tells the admin where they belong.
     */
    private static void exportForProxy(JavaPlugin plugin) {
        Path folder = plugin.getDataFolder().toPath().resolve("bedrock");
        try {
            copy(plugin, PACK_RESOURCE, folder.resolve("porticlegun.mcpack"));
            copy(plugin, MAPPINGS_RESOURCE, folder.resolve("porticlegun_mappings.json"));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not export the Bedrock pack", exception);
            return;
        }
        plugin.getLogger()
                .info("Floodgate is installed but Geyser is not on this server (proxy?). For the 3D gun on Bedrock, "
                        + "copy plugins/PorticleGun/bedrock/porticlegun.mcpack into Geyser's packs folder and "
                        + "porticlegun_mappings.json into its custom_mappings folder, then restart Geyser.");
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
