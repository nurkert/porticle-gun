package eu.nurkert.porticlegun.rift;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

/**
 * Rift screens as native Bedrock forms through Floodgate: big buttons with an icon and a second line, made for touch
 * screens and controllers. The only class that touches the form API; it is created only when Floodgate is present.
 */
final class BedrockForms implements ScreenRenderer {

    private final JavaPlugin plugin;

    BedrockForms(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void show(Player player, RiftScreen screen) {
        List<Runnable> actions = new ArrayList<>();
        SimpleForm.Builder form =
                SimpleForm.builder().title(ChatColor.stripColor(screen.title())).content(content(screen.body()));
        for (RiftScreen.Button button : screen.buttons()) {
            add(form, button);
            actions.add(button.action());
        }
        if (screen.exit() != null) {
            add(form, screen.exit());
            actions.add(screen.exit().action());
        }
        form.validResultHandler(response -> {
            int clicked = response.clickedButtonId();
            if (clicked >= 0 && clicked < actions.size()) {
                // forms answer on Geyser's network thread
                Bukkit.getScheduler().runTask(plugin, actions.get(clicked));
            }
        });
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
    }

    @Override
    public void prompt(Player player, RiftPrompt prompt) {
        CustomForm.Builder form = CustomForm.builder().title(ChatColor.stripColor(prompt.title()));
        int input = 0;
        if (!prompt.body().isEmpty()) {
            form.label(content(prompt.body()));
            input = 1;
        }
        form.input(ChatColor.stripColor(prompt.label()), "", prompt.initial());
        int index = input;
        form.validResultHandler(response -> {
            String text = response.asInput(index);
            Bukkit.getScheduler().runTask(plugin, () -> prompt.submit().accept(text == null ? "" : text));
        });
        form.closedOrInvalidResultHandler(() -> Bukkit.getScheduler().runTask(plugin, prompt.cancelled()));
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
    }

    @Override
    public void close(Player player) {
        FloodgateApi.getInstance().closeForm(player.getUniqueId());
    }

    /**
     * The text above the buttons in the form's own colour: white and light grey (made for Java's dark windows) give
     * way to it, the colours that read on any background (the tank bar, green, aqua, yellow, red) stay.
     */
    private static String content(List<String> lines) {
        return String.join("\n", lines).replace("§7", "§r").replace("§f", "§r");
    }

    private static void add(SimpleForm.Builder form, RiftScreen.Button button) {
        // the form's own text colour on the light buttons; only the current destination stands out in dark green
        String label = ChatColor.stripColor(button.label());
        String text = button.selected() ? "§2§l✔ §r§2" + label : label;
        if (button.detail() != null) {
            text += "\n§8" + ChatColor.stripColor(button.detail());
        }
        if (button.icon() != null) {
            form.button(text, FormImage.Type.PATH, button.icon().bedrockPath());
        } else {
            form.button(text);
        }
    }
}
