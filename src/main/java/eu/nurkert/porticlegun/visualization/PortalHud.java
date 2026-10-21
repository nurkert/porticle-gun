package eu.nurkert.porticlegun.visualization;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.resourcepack.ResourcePackHandler;
import eu.nurkert.porticlegun.util.BedrockSupport;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * The portal status overlay: two rings around the crosshair, one per portal, filled while the portal is open and drawn
 * in the portal's colour. The rings are glyphs from the PorticleGun resource pack (see generate_resourcepack.py),
 * sent as a title because Minecraft draws titles around the screen centre. It stays visible while the gun is held.
 *
 * <p>Java players only get it once their client has loaded the pack. Bedrock players only get it when
 * {@code hud.bedrock-overlay} is enabled, because Bedrock's glyph rendering cannot be verified here. Everyone else
 * keeps the classic title from {@link TitleHandler}.
 */
public class PortalHud implements Listener {

    // glyphs of the PorticleGun resource pack, see HUD_GLYPHS in scripts/generate_resourcepack.py
    static final char LEFT_CLOSED = (char) 0xE9A0;
    static final char LEFT_OPEN = (char) 0xE9A1;
    static final char RIGHT_CLOSED = (char) 0xE9A2;
    static final char RIGHT_OPEN = (char) 0xE9A3;
    static final char GAP = (char) 0xE9A4;
    // balances the spacing Minecraft counts after the last glyph when centring, so the rings sit symmetrically
    static final char LEAD = (char) 0xE9A5;

    private static final long REFRESH_TICKS = 10;
    private static final int STAY_TICKS = 25;
    private static final int FADE_OUT_TICKS = 5;

    private final PorticleGun plugin;
    private final ResourcePackHandler resourcePacks;
    private final Set<UUID> showing = new HashSet<>();
    private final BukkitTask task;

    public PortalHud(PorticleGun plugin, ResourcePackHandler resourcePacks) {
        this.plugin = plugin;
        this.resourcePacks = resourcePacks;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, REFRESH_TICKS, REFRESH_TICKS);
    }

    public boolean usesOverlay(Player player) {
        FileConfiguration config = plugin.getConfig();
        if (BedrockSupport.isBedrock(player)) {
            return config.getBoolean("hud.bedrock-overlay", false);
        }
        return config.getBoolean("hud.overlay", true) && resourcePacks.isLoaded(player);
    }

    /**
     * Shows the overlay for the given gun if the player can display it.
     *
     * @return whether it was shown; otherwise the caller shows the classic title
     */
    public boolean show(Player player, String gunID) {
        if (!usesOverlay(player)) {
            return false;
        }
        GunColors colors = plugin.getGunColors().getColors(gunID);
        int primary = colors.getPrimary().getBukkitColor().asRGB();
        int secondary = colors.getSecondary().getBukkitColor().asRGB();
        boolean primaryOpen = plugin.getActivePortals().hasPrimaryPortal(gunID);
        boolean secondaryOpen = plugin.getActivePortals().hasSecondaryPortal(gunID);

        // the left ring belongs to the button on the left: the mouse's left click (primary), but a controller's left
        // trigger fires the secondary portal
        String overlay = BedrockSupport.hasMirroredTriggers(player)
                ? build(secondary, secondaryOpen, primary, primaryOpen, true)
                : build(primary, primaryOpen, secondary, secondaryOpen, BedrockSupport.isBedrock(player));
        player.sendTitle(overlay, "", 0, STAY_TICKS, FADE_OUT_TICKS);
        showing.add(player.getUniqueId());
        return true;
    }

    /**
     * Builds the overlay line.
     *
     * @param bedrock Bedrock has no space glyph from the Java font, so a plain gap is used there
     */
    static String build(int leftRgb, boolean leftOpen, int rightRgb, boolean rightOpen, boolean bedrock) {
        return (bedrock ? "" : String.valueOf(LEAD))
                + color(leftRgb)
                + (leftOpen ? LEFT_OPEN : LEFT_CLOSED)
                + (bedrock ? "  " : String.valueOf(GAP))
                + color(rightRgb)
                + (rightOpen ? RIGHT_OPEN : RIGHT_CLOSED);
    }

    private static String color(int rgb) {
        return ChatColor.of(String.format("#%06X", rgb & 0xFFFFFF)).toString();
    }

    private void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String gunID = ItemHandler.isValidGun(player.getInventory().getItemInMainHand());
            if (gunID != null && show(player, gunID)) {
                continue;
            }
            if (showing.remove(player.getUniqueId())) {
                player.resetTitle();
            }
        }
    }

    @EventHandler
    public void on(PlayerQuitEvent event) {
        showing.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        task.cancel();
        for (UUID uuid : showing) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.resetTitle();
            }
        }
        showing.clear();
    }
}
