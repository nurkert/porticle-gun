package eu.nurkert.porticlegun.rift;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Rift screens as native Java dialogs (Minecraft 1.21.6+), built with Paper's dialog API. The only class that touches
 * that API: it is created only after {@link RiftScreens} found it, so the plugin still runs on Spigot, where the chest
 * menus are used instead. On 26.x servers the buttons show their icons as sprites from the client's own atlas.
 */
final class PaperDialogs implements ScreenRenderer {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    // a click may come twice before the next window arrives; the action checks the gun again anyway
    private static final ClickCallback.Options CLICKS = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(Duration.ofMinutes(10))
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
        DialogBase base = DialogBase.builder(title(screen.title()))
                .canCloseWithEscape(true)
                .pause(false)
                // the window stays until the server replaces or closes it right away: no flicker, and no "waiting for
                // response" screen, which closing a dialog does not end
                .afterAction(DialogBase.DialogAfterAction.NONE)
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
    public void prompt(Player player, RiftPrompt prompt) {
        List<DialogBody> body =
                prompt.body().isEmpty() ? List.of() : List.of(DialogBody.plainMessage(lines(prompt.body()), WIDE));
        DialogBase base = DialogBase.builder(title(prompt.title()))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(body)
                .inputs(List.of(DialogInput.text("text", plain(prompt.label()))
                        .initial(prompt.initial())
                        .maxLength(prompt.maxLength())
                        .width(WIDE)
                        .build()))
                .build();
        ActionButton confirm = ActionButton.builder(plain(prompt.confirm()))
                .width(NARROW)
                .action(DialogAction.customClick(
                        (response, audience) -> {
                            String text = response.getText("text");
                            run(player, () -> prompt.submit().accept(text == null ? "" : text));
                        },
                        CLICKS))
                .build();
        ActionButton cancel = ActionButton.builder(plain(prompt.cancel()))
                .width(NARROW)
                .action(DialogAction.customClick((response, audience) -> run(player, prompt.cancelled()), CLICKS))
                .build();
        DialogType type = DialogType.confirmation(confirm, cancel);
        audience(player)
                .showDialog(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    @Override
    public void close(Player player) {
        audience(player).closeDialog();
    }

    private ActionButton button(Player player, RiftScreen.Button button, int width) {
        // white on the grey buttons like every vanilla button; only the current destination stands out in green
        Component label = button.selected()
                ? plain(button.label()).color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                : plain(button.label());
        if (sprites && button.icon() != null) {
            label = icon(button.icon()).append(Component.text(" ")).append(label);
        }
        List<String> tooltip = new ArrayList<>(button.tooltip());
        if (button.detail() != null && tooltip.isEmpty()) {
            tooltip.add(button.detail());
        }
        ActionButton.Builder builder = ActionButton.builder(label)
                .width(width)
                .action(DialogAction.customClick((response, audience) -> run(player, button.action()), CLICKS));
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

    /** Body and tooltip text: no grey (it reads poorly and looks off next to vanilla), white instead. */
    private static Component lines(List<String> lines) {
        return LEGACY.deserialize(
                withoutSymbols(String.join("\n", lines)).replace("§7", "§f").replace("§8", "§f"));
    }

    /** Titles are plain white, like the titles of vanilla screens. */
    private static Component title(String title) {
        return plain(title);
    }

    private static Component plain(String text) {
        return Component.text(withoutSymbols(ChatColor.stripColor(text)));
    }

    /**
     * Drops pictographs (arrows, ticks, house and skull signs, the tank bar's squares) from dialog texts: next to
     * vanilla's plain buttons they look cheap, and the buttons have real item icons anyway.
     */
    static String withoutSymbols(String text) {
        StringBuilder result = new StringBuilder();
        boolean dropSpace = false; // the space after a dropped symbol goes with it
        boolean colourCode = false;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (codePoint > 0x7F && (type == Character.OTHER_SYMBOL || type == Character.MATH_SYMBOL)) {
                dropSpace = true;
                continue;
            }
            if (codePoint == ' ' && dropSpace) {
                dropSpace = false;
                continue;
            }
            if (!colourCode && codePoint != '§') {
                dropSpace = false;
            }
            colourCode = codePoint == '§';
            result.appendCodePoint(codePoint);
        }
        return result.toString().strip();
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
