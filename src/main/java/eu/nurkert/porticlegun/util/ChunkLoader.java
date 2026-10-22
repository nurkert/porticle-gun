package eu.nurkert.porticlegun.util;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads (and if needed generates) chunks without freezing the server where the server allows it. Paper loads and
 * generates them in the background ({@code World#getChunkAtAsync}); on Spigot they are loaded on the main thread as
 * before.
 */
public final class ChunkLoader {

    private static final Method GET_CHUNK_AT_ASYNC = findAsyncMethod();

    private ChunkLoader() {}

    /**
     * Runs the action on the main thread once the chunk at the given block position is loaded.
     */
    public static void whenLoaded(JavaPlugin plugin, World world, int blockX, int blockZ, Runnable action) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        if (world.isChunkLoaded(chunkX, chunkZ) || GET_CHUNK_AT_ASYNC == null) {
            action.run();
            return;
        }
        CompletableFuture<?> future;
        try {
            future = (CompletableFuture<?>) GET_CHUNK_AT_ASYNC.invoke(world, chunkX, chunkZ, true);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            action.run();
            return;
        }
        future.whenComplete((chunk, error) -> {
            if (plugin.isEnabled()) {
                // Paper completes on the main thread; this keeps it there on any server
                plugin.getServer().getScheduler().runTask(plugin, action);
            }
        });
    }

    private static Method findAsyncMethod() {
        try {
            return World.class.getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }
}
