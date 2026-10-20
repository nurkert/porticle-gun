package eu.nurkert.porticlegun.util;

import java.util.UUID;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

/**
 * The only class that touches the Floodgate API. It is loaded only after {@link BedrockSupport} found Floodgate, so
 * the plugin runs without it.
 */
final class FloodgateBridge {

    private FloodgateBridge() {}

    static void check() {
        FloodgateApi.getInstance();
    }

    static boolean isBedrock(UUID uuid) {
        return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
    }

    static BedrockSupport.InputMode getInputMode(UUID uuid) {
        FloodgatePlayer player = FloodgateApi.getInstance().getPlayer(uuid);
        if (player == null || player.getInputMode() == null) {
            return BedrockSupport.InputMode.UNKNOWN;
        }
        return switch (player.getInputMode()) {
            case KEYBOARD_MOUSE -> BedrockSupport.InputMode.KEYBOARD_MOUSE;
            case TOUCH -> BedrockSupport.InputMode.TOUCH;
            case CONTROLLER -> BedrockSupport.InputMode.CONTROLLER;
            case VR -> BedrockSupport.InputMode.VR;
            default -> BedrockSupport.InputMode.UNKNOWN;
        };
    }
}
