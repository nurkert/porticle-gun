package eu.nurkert.porticlegun.rift;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Rift screens as native Java dialogs (Minecraft 1.21.6+), built with Paper's dialog API. The only class that touches
 * that API: it is created only after {@link RiftScreens} found it, so the plugin still runs on Spigot, where the chest
 * menus are used instead. On 26.x servers the buttons show their icons as sprites from the client's own atlas.
 */
final class PaperDialogs implements ScreenRenderer {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    // a dialog button works once; the dialog is replaced right after a click anyway
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(30))
            .build();
    private static final int WIDE = 310;
    private static final int NARROW = 150;

    private final JavaPlugin plugin;
    private final boolean sprites;

    PaperDialogs(JavaPlugin plugin) {
        this.plugin = plugin;
        this.sprites = spritesSupported();
    }

    @Override
    public void show(Player player, RiftScreen screen) {
        List<DialogBody> body = new ArrayList<>();
        Component text = lines(screen.body());
        if (screen.item() != null) {
            body.add(DialogBody.item(screen.item())
                    .description(DialogBody.plainMessage(text, 250))
                    .showTooltip(true)
                    .showDecorations(true)
                    .build());
        } else if (!screen.body().isEmpty()) {
            body.add(DialogBody.plainMessage(text, WIDE));
        }
        int width = screen.columns() > 1 ? NARROW : WIDE;
        List<ActionButton> buttons = new ArrayList<>();
        for (RiftScreen.Button button : screen.buttons()) {
            buttons.add(button(player, button, width));
        }
        DialogBase base = DialogBase.builder(LEGACY.deserialize(screen.title()))
                .canCloseWithEscape(true)
                .pause(false)
                // the next screen (or closing) is always sent by the server, so nothing flickers in between
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body)
                .build();
        var type = DialogType.multiAction(buttons).columns(Math.max(1, screen.columns()));
        if (screen.exit() != null) {
            type.exitAction(button(player, screen.exit(), NARROW));
        }
        DialogType dialogType = type.build();
        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(dialogType));
        audience(player).showDialog(dialog);
    }

    @Override
    public void close(Player player) {
        audience(player).closeDialog();
    }

    private ActionButton button(Player player, RiftScreen.Button button, int width) {
        Component label = LEGACY.deserialize((button.selected() ? "§a§l✔ §r" : "") + button.label());
        if (sprites && button.icon() != null) {
            label = icon(button.icon()).append(Component.text(" ")).append(label);
        }
        List<String> tooltip = new ArrayList<>(button.tooltip());
        if (button.detail() != null && tooltip.isEmpty()) {
            tooltip.add("§7" + button.detail());
        }
        ActionButton.Builder builder = ActionButton.builder(label)
                .width(width)
                .action(DialogAction.customClick((response, audience) -> run(player, button.action()), ONCE));
        if (!tooltip.isEmpty()) {
            builder.tooltip(lines(tooltip));
        }
        return builder.build();
    }

    private void run(Player player, Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }

    private static Component icon(RiftIcon icon) {
        return Component.object(ObjectContents.sprite(
                Key.key(Key.MINECRAFT_NAMESPACE, icon.javaAtlas()),
                Key.key(Key.MINECRAFT_NAMESPACE, icon.javaSprite())));
    }

    private static Component lines(List<String> lines) {
        return LEGACY.deserialize(String.join("\n", lines));
    }

    private static Audience audience(Player player) {
        // on Paper every player is an audience; the plugin compiles against Spigot's Player
        return (Audience) player;
    }

    /**
     * Sprites in text exist since 1.21.9; the separate item atlas the icons rely on is only checked for 26.x, so older
     * servers show the buttons without icons rather than with missing textures.
     */
    private static boolean spritesSupported() {
        try {
            Class.forName("net.kyori.adventure.text.object.ObjectContents");
            String version = Bukkit.getBukkitVersion();
            int dot = version.indexOf('.');
            return dot > 0 && Integer.parseInt(version.substring(0, dot)) >= 26;
        } catch (ClassNotFoundException | LinkageError | NumberFormatException exception) {
            return false;
        }
    }
}
