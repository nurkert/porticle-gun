package eu.nurkert.porticlegun.persistence;

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
import org.bukkit.plugin.java.JavaPlugin;
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

    private File file;
    private FileConfiguration config;

    private boolean dirty;
    private BukkitTask pendingSave;

    private final Object writeLock = new Object();
    private long snapshotCounter;
    private long lastWrittenSnapshot;

    private final JavaPlugin plugin;

    public PersistentHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        file = new File(dataFolder, FILENAME);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public List<String> getSection(String path) {
        if (config.getConfigurationSection(path) == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(config.getConfigurationSection(path).getKeys(false));
    }

    public void set(String path, Object value) {
        config.set(path, value);
        markDirty();
    }

    public String get(String path) {
        return config.getString(path);
    }

    public boolean exists(String path) {
        return config.get(path) != null;
    }

    /**
     * Writes all pending changes to disk immediately, on the calling thread.
     */
    public void saveAll() {
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

    public void reload() {
        saveAll();
        config = YamlConfiguration.loadConfiguration(file);
    }

    private void markDirty() {
        dirty = true;
        if (pendingSave == null) {
            pendingSave = Bukkit.getScheduler().runTaskLater(plugin, this::saveAsync, SAVE_DELAY_TICKS);
        }
    }

    private void saveAsync() {
        pendingSave = null;
        if (!dirty) {
            return;
        }
        dirty = false;
        String content = config.saveToString();
        long snapshot = ++snapshotCounter;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(content, snapshot));
    }

    private void write(String content, long snapshot) {
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
                plugin.getLogger().log(Level.SEVERE, "Could not save " + FILENAME, exception);
            }
        }
    }
}
