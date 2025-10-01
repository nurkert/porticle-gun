package eu.nurkert.porticlegun.handlers.compat;

import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Utility class that detects whether a player joined through Geyser/Floodgate
 * without requiring the dependencies at compile time.
 */
public final class GeyserCompatibility {

    private static final boolean FLOODGATE_AVAILABLE;
    private static final Object FLOODGATE_API_INSTANCE;
    private static final Method FLOODGATE_IS_PLAYER_METHOD;

    private static final boolean GEYSER_API_AVAILABLE;
    private static final Object GEYSER_API_INSTANCE;
    private static final Method GEYSER_CONNECTION_BY_UUID_METHOD;

    static {
        FLOODGATE_API_INSTANCE = loadFloodgateApiInstance();
        FLOODGATE_IS_PLAYER_METHOD = loadFloodgateIsPlayerMethod(FLOODGATE_API_INSTANCE);
        FLOODGATE_AVAILABLE = FLOODGATE_API_INSTANCE != null && FLOODGATE_IS_PLAYER_METHOD != null;

        GEYSER_API_INSTANCE = loadGeyserApiInstance();
        GEYSER_CONNECTION_BY_UUID_METHOD = loadGeyserConnectionByUuidMethod(GEYSER_API_INSTANCE);
        GEYSER_API_AVAILABLE = GEYSER_API_INSTANCE != null && GEYSER_CONNECTION_BY_UUID_METHOD != null;
    }

    private GeyserCompatibility() {
    }

    public static boolean isBedrockPlayer(Player player) {
        if (player == null) {
            return false;
        }

        UUID uuid = player.getUniqueId();

        if (FLOODGATE_AVAILABLE) {
            try {
                Object result = FLOODGATE_IS_PLAYER_METHOD.invoke(FLOODGATE_API_INSTANCE, uuid);
                if (result instanceof Boolean && (Boolean) result) {
                    return true;
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }

        if (GEYSER_API_AVAILABLE) {
            try {
                Object connection = GEYSER_CONNECTION_BY_UUID_METHOD.invoke(GEYSER_API_INSTANCE, uuid);
                if (connection != null) {
                    return true;
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }

        return false;
    }

    private static Object loadFloodgateApiInstance() {
        try {
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = floodgateApiClass.getMethod("getInstance");
            return getInstance.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static Method loadFloodgateIsPlayerMethod(Object apiInstance) {
        if (apiInstance == null) {
            return null;
        }

        try {
            Class<?> floodgateApiClass = apiInstance.getClass();
            return floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Object loadGeyserApiInstance() {
        try {
            Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Method apiMethod = geyserApiClass.getMethod("api");
            return apiMethod.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static Method loadGeyserConnectionByUuidMethod(Object apiInstance) {
        if (apiInstance == null) {
            return null;
        }

        try {
            return apiInstance.getClass().getMethod("connectionByUuid", UUID.class);
        } catch (NoSuchMethodException ignored) {
            for (Class<?> iface : apiInstance.getClass().getInterfaces()) {
                try {
                    return iface.getMethod("connectionByUuid", UUID.class);
                } catch (NoSuchMethodException ignoredAgain) {
                    // continue searching
                }
            }
            return null;
        }
    }
}
