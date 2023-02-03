package eu.nurkert.porticlegun.handlers;

import eu.nurkert.porticlegun.PorticleGun;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public class PersitentHandler {
    // Singleton construction
    final private static PersitentHandler instance = new PersitentHandler();

    static PortalsFile portalsFile;
    static FileConfiguration config;

    private PersitentHandler() {
        portalsFile = new PortalsFile();
        config = portalsFile.getConfig();
    }

    /**
     * @return the singleton instance
     */
    public static PersitentHandler getInstance() {
        return instance;
    }


    /**
     * @param path the path to the section
     * @return a list of all keys in the section
     */
    public static List<String> getSection(String path) {
        return new ArrayList<String>(config.getConfigurationSection(path).getKeys(false)).stream().map(key -> Base64.getDecoder().decode(key).toString()).collect(Collectors.toList());
    }

    /**
     * Encodes a path Base64
     */
    private static String encodePath(String path) {
        StringBuilder encoded = new StringBuilder();
        for(String key : path.split("."))
            encoded.append(Base64.getEncoder().encodeToString(key.getBytes()));
        return encoded.toString();
    }

    /**
     * Decodes a path from Base64
     */
    private static String decodePath(String path) {
        StringBuilder decoded = new StringBuilder();
        for (String key : path.split("."))
            decoded.append(new String(Base64.getDecoder().decode(key)));
        return decoded.toString();
    }

    public static void set(String path, Object value) {
        config.set(encodePath(path), value);
        portalsFile.save();
    }

    public static String get(String path) {
        return Base64.getDecoder().decode(config.getString(encodePath(path))).toString();
    }

    public static boolean exists(String path) {
        return config.get(encodePath(path)) != null;
    }

    public class PortalsFile {

        final private String filename = "portals.yml";
        String path;

        File dataFolder, portalYML;
        FileConfiguration portals;

        public PortalsFile() {
            init();
        }

        private void init() {
            if (!PorticleGun.getInstance().getDataFolder().exists())
                PorticleGun.getInstance().getDataFolder().mkdirs();
            dataFolder = PorticleGun.getInstance().getDataFolder();

            path = dataFolder.getPath();

            if (!fileExists()) {
                try {
                    new File(path, filename).createNewFile();
                } catch (IOException ignored) {}
            }
            portalYML = new File(path, filename);
            portals = YamlConfiguration.loadConfiguration(portalYML);
        }

        private boolean fileExists() {
            return new File(path, filename).exists();
        }

        public FileConfiguration getConfig() {
            return portals;
        }

        public void save() {
            try {
                portals.save(portalYML);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
