package eu.nurkert.porticlegun.persistence;

import eu.nurkert.porticlegun.PorticleGun;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

/**
 * Stores portals and gun settings in portals.yml.
 *
 * <p>Changes are collected and written shortly after the last change: the file content is created on the main thread
 * and written to disk asynchronously, so placing a portal never blocks the server on disk I/O. {@link #saveAll()}
 * writes synchronously and is used when the plugin is disabled or reloaded.
 */
public class PersistentHandler {

    private static final String FILENAME = "portals.yml";
    private static final long SAVE_DELAY_TICKS = 20L;

    // Singleton construction
    private static final PersistentHandler instance = new PersistentHandler();

    private static File file;
    private static FileConfiguration config;

    private static boolean dirty;
    private static BukkitTask pendingSave;

    private static final Object writeLock = new Object();
    private static long snapshotCounter;
    private static long lastWrittenSnapshot;

    private PersistentHandler() {
        File dataFolder = PorticleGun.getInstance().getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        file = new File(dataFolder, FILENAME);
        config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * @return the singleton instance
     */
    public static PersistentHandler getInstance() {
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
        markDirty();
    }

    public static String get(String path) {
        return config.getString(path);
    }

    public static boolean exists(String path) {
        return config.get(path) != null;
    }

    /**
     * Writes all pending changes to disk immediately, on the calling thread.
     */
    public static void saveAll() {
        if (pendingSave != null) {
            pendingSave.cancel();
            pendingSave = null;
        }
        if (!dirty) {
            return;
        }
        dirty = false;
        write(config.saveToString(), ++snapshotCounter);
    }

    public static void reload() {
        saveAll();
        config = YamlConfiguration.loadConfiguration(file);
    }

    private static void markDirty() {
        dirty = true;
        if (pendingSave == null) {
            pendingSave = Bukkit.getScheduler()
                    .runTaskLater(PorticleGun.getInstance(), PersistentHandler::saveAsync, SAVE_DELAY_TICKS);
        }
    }

    private static void saveAsync() {
        pendingSave = null;
        if (!dirty) {
            return;
        }
        dirty = false;
        String content = config.saveToString();
        long snapshot = ++snapshotCounter;
        Bukkit.getScheduler().runTaskAsynchronously(PorticleGun.getInstance(), () -> write(content, snapshot));
    }

    private static void write(String content, long snapshot) {
        synchronized (writeLock) {
            // a newer snapshot may already have been written synchronously
            if (snapshot <= lastWrittenSnapshot) {
                return;
            }
            Path target = file.toPath();
            Path temp = target.resolveSibling(FILENAME + ".tmp");
            try {
                Files.writeString(temp, content, StandardCharsets.UTF_8);
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                lastWrittenSnapshot = snapshot;
            } catch (IOException exception) {
                PorticleGun.getInstance().getLogger().log(Level.SEVERE, "Could not save " + FILENAME, exception);
            }
        }
    }
}
