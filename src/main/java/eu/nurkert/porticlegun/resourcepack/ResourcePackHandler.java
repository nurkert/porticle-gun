package eu.nurkert.porticlegun.resourcepack;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.util.BedrockSupport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Sends the PorticleGun resource pack (3D gun model) to Java players in addition to any server or other plugin pack.
 *
 * <p>By default the pack is downloaded from the GitHub release matching the plugin version; its SHA-1 is embedded in
 * the jar at build time. Development builds have no release, so a custom URL has to be configured for them.
 */
public class ResourcePackHandler implements Listener {

    private static final String DEFAULT_URL =
            "https://github.com/nurkert/porticle-gun/releases/download/v%1$s/PorticleGun-resourcepack-%1$s.zip";
    private static final UUID PACK_ID =
            UUID.nameUUIDFromBytes("porticlegun:resourcepack".getBytes(StandardCharsets.UTF_8));

    private boolean enabled;
    private boolean required;
    private String url;
    private byte[] sha1;
    private String prompt;

    public ResourcePackHandler() {
        reload();
    }

    public void reload() {
        PorticleGun plugin = PorticleGun.getInstance();
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean("resource-pack.enabled", true);
        required = config.getBoolean("resource-pack.required", false);
        prompt = emptyToNull(config.getString("resource-pack.prompt", ""));
        url = null;
        sha1 = null;
        if (!enabled) {
            return;
        }

        String configuredUrl = emptyToNull(config.getString("resource-pack.url", ""));
        String configuredSha1 = emptyToNull(config.getString("resource-pack.sha1", ""));
        Properties embedded = loadEmbeddedProperties();
        String version = embedded.getProperty("version", "");

        if (configuredUrl != null) {
            url = configuredUrl;
            sha1 = configuredSha1 != null ? parseSha1(configuredSha1) : null;
        } else if (version.isEmpty() || version.endsWith("-SNAPSHOT")) {
            plugin.getLogger()
                    .info("Development build: the resource pack is not sent unless resource-pack.url is configured.");
            enabled = false;
            return;
        } else {
            url = String.format(DEFAULT_URL, version);
            sha1 = parseSha1(embedded.getProperty("sha1", ""));
        }
    }

    @EventHandler
    public void on(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Bedrock players get the Bedrock pack from Geyser instead
        if (!enabled || url == null || BedrockSupport.isBedrock(player)) {
            return;
        }
        player.addResourcePack(PACK_ID, url, sha1, prompt, required);
    }

    private static Properties loadEmbeddedProperties() {
        Properties properties = new Properties();
        try (InputStream in = PorticleGun.getInstance().getResource("resourcepack.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException exception) {
            PorticleGun.getInstance()
                    .getLogger()
                    .log(Level.WARNING, "Could not read the embedded resource pack information", exception);
        }
        return properties;
    }

    private static byte[] parseSha1(String hex) {
        try {
            byte[] bytes = HexFormat.of().parseHex(hex.trim());
            return bytes.length == 20 ? bytes : null;
        } catch (IllegalArgumentException exception) {
            PorticleGun.getInstance()
                    .getLogger()
                    .warning("Ignoring invalid resource pack SHA-1 '" + hex + "', the pack is sent without a hash.");
            return null;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
