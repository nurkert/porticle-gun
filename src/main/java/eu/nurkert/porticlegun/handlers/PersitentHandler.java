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

    private static PortalsFile portalsFile;
    private static FileConfiguration config;

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

    public static List<String> getSection(String path) {
        if (config.getConfigurationSection(path) == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(config.getConfigurationSection(path).getKeys(false));
    }

    public static void set(String path, Object value) {
        config.set(path, value);
        portalsFile.save();
    }

    public static String get(String path) {
        return config.getString(path);
    }

    public static boolean exists(String path) {
        return config.get(path) != null;
    }

    public static void saveAll() {
        portalsFile.save();
    }

    public static void reload() {
        portalsFile.reload();
        config = portalsFile.getConfig();
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

        public void reload() {
            portals = YamlConfiguration.loadConfiguration(portalYML);
        }
    }
}
