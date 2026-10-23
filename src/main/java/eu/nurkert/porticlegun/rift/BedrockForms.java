package eu.nurkert.porticlegun.rift;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
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
        SimpleForm.Builder form = SimpleForm.builder()
                .title(ChatColor.stripColor(screen.title()))
                .content(String.join("\n", screen.body()));
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
    public void close(Player player) {
        FloodgateApi.getInstance().closeForm(player.getUniqueId());
    }

    private static void add(SimpleForm.Builder form, RiftScreen.Button button) {
        // buttons are light grey on Bedrock: the Java colours (made for dark backgrounds) are replaced by dark ones
        String label = ChatColor.stripColor(button.label());
        String text = button.selected() ? "§2§l✔ " + label : "§0" + label;
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
