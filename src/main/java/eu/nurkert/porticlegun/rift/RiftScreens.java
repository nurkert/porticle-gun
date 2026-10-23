package eu.nurkert.porticlegun.rift;

import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.util.BedrockSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The menu of the interdimensional gun as native windows: a dialog on Java (Paper 1.21.6+), a form on Bedrock (with
 * Floodgate). Choosing a destination, homes and the rift atlas happen there; only filling the tank stays a chest,
 * because only a chest has a slot to put items in. Where neither is available (Spigot, older servers, or
 * {@code native-menus: false}) the chest menu of {@link RiftMenu} does everything.
 */
final class RiftScreens {

    /** The gun a screen belongs to; a click does nothing once that gun left its slot. */
    private record Context(Player player, int slot, String gunId, ScreenRenderer renderer) {

        ItemStack gun() {
            ItemStack gun = player.getInventory().getItem(slot);
            return RiftItems.isGun(gun) && gunId.equals(RiftItems.gunId(gun)) ? gun : null;
        }
    }

    private final RiftModule module;
    private final ScreenRenderer dialogs;
    private ScreenRenderer forms;
    private boolean formsChecked;

    RiftScreens(RiftModule module) {
        this.module = module;
        ScreenRenderer paper = null;
        // checked here: loading PaperDialogs itself fails where Paper's dialog API is missing
        if (classExists("io.papermc.paper.dialog.Dialog")) {
            try {
                paper = new PaperDialogs(module.plugin());
            } catch (LinkageError exception) {
                module.plugin().getLogger().log(Level.WARNING, "Native dialogs are not available.", exception);
            }
        }
        this.dialogs = paper;
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            return false;
        }
    }

    /** Opens the gun's menu: natively where possible, otherwise as a chest. */
    void open(Player player, int slot) {
        ItemStack gun = player.getInventory().getItem(slot);
        if (!RiftItems.isGun(gun)) {
            return;
        }
        ScreenRenderer renderer = renderer(player);
        if (renderer == null) {
            module.menu().open(player, slot);
            return;
        }
        if (RiftItems.claimPlayerHomes(gun, player, module.storage())) {
            player.getInventory().setItem(slot, gun);
        }
        main(new Context(player, slot, RiftItems.gunId(gun), renderer));
    }

    private ScreenRenderer renderer(Player player) {
        if (!module.settings().nativeMenus()) {
            return null;
        }
        if (BedrockSupport.isBedrock(player)) {
            return forms();
        }
        return dialogs;
    }

    private ScreenRenderer forms() {
        if (!formsChecked) {
            formsChecked = true;
            try {
                forms = new BedrockForms(module.plugin());
            } catch (LinkageError exception) {
                module.plugin().getLogger().log(Level.WARNING, "Bedrock forms are not available.", exception);
            }
        }
        return forms;
    }

    /**
     * The main window: the gun with destination, tank and compass at the top, below a few big entries that each open
     * their own window (worlds, journeys, atlas, homes) and the tank.
     */
    private void main(Context context) {
        Player player = context.player();
        ItemStack gun = context.gun();
        if (gun == null) {
            return;
        }
        RiftSettings settings = module.settings();
        RiftDestination selected = RiftItems.destination(gun);
        List<String> body = new ArrayList<>();
        body.add(
                selected == null
                        ? text(player, "rift.screen.destination-none")
                        : text(
                                player,
                                "rift.screen.destination",
                                Map.of("destination", RiftNames.describe(selected, player, gun))));
        body.add(text(
                player,
                "rift.screen.tank",
                Map.of(
                        "bar", RiftItems.tankBar(RiftItems.charges(gun), settings.tankCapacity()),
                        "charges", String.valueOf(RiftItems.charges(gun)),
                        "capacity", String.valueOf(settings.tankCapacity()))));
        body.add(
                RiftItems.isAttuned(gun)
                        ? text(player, "rift.screen.compass-active", Map.of("time", RiftItems.remaining(gun)))
                        : text(player, "rift.screen.compass-idle"));
        body.add(text(player, "rift.screen.hint"));

        List<RiftScreen.Button> buttons = new ArrayList<>();
        buttons.add(worldsButton(context, selected));
        buttons.add(journeysButton(context, selected));
        buttons.add(atlasButton(context, gun, selected));
        if (settings.homes() > 0) {
            buttons.add(homesButton(context, gun, selected));
        }
        buttons.add(new RiftScreen.Button(
                text(player, "rift.screen.tank-button"),
                text(
                        player,
                        "rift.screen.tank-detail",
                        Map.of(
                                "charges", String.valueOf(RiftItems.charges(gun)),
                                "capacity", String.valueOf(settings.tankCapacity()))),
                lines(player, "rift.screen.tank-lore", Map.of("rifts", String.valueOf(settings.riftsPerFluid()))),
                RiftIcon.TANK,
                false,
                act(context, current -> openTank(context))));

        context.renderer()
                .show(
                        player,
                        new RiftScreen(
                                text(player, "rift.screen.title"),
                                gun.clone(),
                                body,
                                buttons,
                                2,
                                RiftScreen.Button.of(
                                        text(player, "rift.screen.close"),
                                        null,
                                        act(context, current -> context.renderer()
                                                .close(player)))));
    }

    private RiftScreen.Button worldsButton(Context context, RiftDestination selected) {
        Player player = context.player();
        boolean world = selected != null
                && (selected.type() == RiftDestination.Type.WORLD || selected.type() == RiftDestination.Type.SPAWN);
        return new RiftScreen.Button(
                text(player, "rift.screen.worlds"),
                world ? RiftNames.describe(selected, player) : text(player, "rift.screen.worlds-detail"),
                List.of(),
                RiftIcon.OVERWORLD,
                world,
                act(context, gun -> worlds(context)));
    }

    private RiftScreen.Button journeysButton(Context context, RiftDestination selected) {
        Player player = context.player();
        boolean journey = selected != null
                && (selected.type() == RiftDestination.Type.BACK || selected.type() == RiftDestination.Type.DEATH);
        return new RiftScreen.Button(
                text(player, "rift.screen.journeys"),
                journey ? RiftNames.describe(selected, player) : text(player, "rift.screen.journeys-detail"),
                List.of(),
                RiftIcon.BACK,
                journey,
                act(context, gun -> journeys(context)));
    }

    /** A random spot and the spawn of every world that is not disabled, side by side. */
    private void worlds(Context context) {
        Player player = context.player();
        RiftDestination selected = RiftItems.destination(context.gun());
        List<RiftScreen.Button> buttons = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (module.settings().disabledWorlds().contains(world.getName())) {
                continue;
            }
            String name = RiftNames.world(world.getName(), player);
            Map<String, String> placeholders = Map.of("world", name);
            buttons.add(destination(
                    context,
                    text(player, "rift.menu.world-random", placeholders),
                    text(player, "rift.screen.random-detail"),
                    lines(player, "rift.menu.world-random-lore", placeholders),
                    RiftIcon.world(world.getEnvironment()),
                    RiftDestination.world(world.getName()),
                    selected));
            buttons.add(destination(
                    context,
                    text(player, "rift.menu.world-spawn", placeholders),
                    text(player, "rift.screen.spawn-detail"),
                    lines(player, "rift.menu.world-spawn-lore", placeholders),
                    RiftIcon.SPAWN,
                    RiftDestination.spawn(world.getName()),
                    selected));
        }
        context.renderer()
                .show(
                        player,
                        new RiftScreen(
                                text(player, "rift.screen.worlds-title"),
                                null,
                                List.of(text(player, "rift.screen.worlds-info")),
                                buttons,
                                2,
                                backTo(context, () -> main(context))));
    }

    /** Back to the previous spot or to the last death. */
    private void journeys(Context context) {
        Player player = context.player();
        RiftDestination selected = RiftItems.destination(context.gun());
        Location back = module.storage().back(player.getUniqueId());
        Location death = module.storage().death(player.getUniqueId());
        List<RiftScreen.Button> buttons = List.of(
                destination(
                        context,
                        text(player, "rift.screen.previous"),
                        place(player, back),
                        lines(player, back == null ? "rift.menu.back-empty" : "rift.menu.back-lore", coordinates(back)),
                        RiftIcon.BACK,
                        RiftDestination.back(),
                        selected),
                destination(
                        context,
                        text(player, "rift.screen.death"),
                        place(player, death),
                        lines(
                                player,
                                death == null ? "rift.menu.death-empty" : "rift.menu.death-lore",
                                coordinates(death)),
                        RiftIcon.DEATH,
                        RiftDestination.death(),
                        selected));
        context.renderer()
                .show(
                        player,
                        new RiftScreen(
                                text(player, "rift.screen.journeys-title"),
                                null,
                                List.of(),
                                buttons,
                                1,
                                backTo(context, () -> main(context))));
    }

    private RiftScreen.Button atlasButton(Context context, ItemStack gun, RiftDestination selected) {
        Player player = context.player();
        boolean structure = selected != null && selected.type() == RiftDestination.Type.STRUCTURE;
        if (RiftItems.isAttuned(gun)) {
            return new RiftScreen.Button(
                    text(player, "rift.screen.atlas"),
                    structure
                            ? RiftNames.describe(selected, player)
                            : text(player, "rift.screen.atlas-detail", Map.of("time", RiftItems.remaining(gun))),
                    lines(player, "rift.menu.atlas.lore", Map.of()),
                    RiftIcon.ATLAS,
                    structure,
                    act(context, current -> atlas(context)));
        }
        return new RiftScreen.Button(
                text(player, "rift.screen.atlas-locked"),
                text(player, "rift.screen.atlas-locked-detail"),
                lines(player, "rift.menu.atlas-locked-lore", Map.of()),
                RiftIcon.ATLAS_LOCKED,
                false,
                act(context, current -> {
                    // a rift compass in the inventory unlocks the atlas right here
                    if (module.menu().attuneFromInventory(player, context.slot())) {
                        atlas(context);
                    } else {
                        main(context);
                    }
                }));
    }

    private RiftScreen.Button homesButton(Context context, ItemStack gun, RiftDestination selected) {
        Player player = context.player();
        int total = module.settings().homes();
        int set = 0;
        for (int home = 0; home < total; home++) {
            if (RiftItems.home(gun, home) != null) {
                set++;
            }
        }
        boolean home = selected != null && selected.type() == RiftDestination.Type.HOME;
        return new RiftScreen.Button(
                text(player, "rift.screen.homes", Map.of("set", String.valueOf(set), "total", String.valueOf(total))),
                home ? RiftNames.describe(selected, player, gun) : text(player, "rift.screen.homes-detail"),
                List.of(),
                RiftIcon.HOMES,
                home,
                act(context, current -> homes(context)));
    }

    /** Every home of the gun; a set one opens its options, an empty one is set right here and chosen. */
    private void homes(Context context) {
        Player player = context.player();
        ItemStack gun = context.gun();
        RiftDestination selected = RiftItems.destination(gun);
        List<RiftScreen.Button> buttons = new ArrayList<>();
        for (int home = 0; home < module.settings().homes(); home++) {
            int number = home;
            Location location = RiftItems.home(gun, home);
            Map<String, String> placeholders = new HashMap<>(coordinates(location));
            placeholders.put("home", String.valueOf(home + 1));
            if (location == null) {
                buttons.add(new RiftScreen.Button(
                        text(player, "rift.menu.home-empty", placeholders),
                        text(player, "rift.screen.home-empty-detail"),
                        lines(player, "rift.menu.home-empty-lore", placeholders),
                        RiftIcon.HOME_EMPTY,
                        false,
                        act(context, current -> {
                            module.menu().setHome(player, context.slot(), number);
                            select(context, RiftDestination.home(number));
                        })));
            } else {
                buttons.add(new RiftScreen.Button(
                        text(player, "rift.screen.home-label", Map.of("name", RiftNames.home(gun, home, player))),
                        place(player, location),
                        List.of(),
                        RiftIcon.HOME,
                        RiftDestination.home(home).equals(selected),
                        act(context, current -> home(context, number))));
            }
        }
        context.renderer()
                .show(
                        player,
                        new RiftScreen(
                                text(player, "rift.screen.homes-title"),
                                null,
                                List.of(text(player, "rift.screen.homes-info")),
                                buttons,
                                1,
                                backTo(context, () -> main(context))));
    }

    private void home(Context context, int home) {
        Player player = context.player();
        ItemStack gun = context.gun();
        Location location = RiftItems.home(gun, home);
        if (location == null) {
            homes(context);
            return;
        }
        Map<String, String> placeholders = new HashMap<>(coordinates(location));
        placeholders.put("home", String.valueOf(home + 1));
        String name = RiftNames.home(gun, home, player);
        List<RiftScreen.Button> buttons = List.of(
                new RiftScreen.Button(
                        text(player, "rift.menu.home-select"),
                        text(player, "rift.menu.home-select-lore", placeholders),
                        List.of(),
                        RiftIcon.TRAVEL,
                        false,
                        act(context, current -> select(context, RiftDestination.home(home)))),
                new RiftScreen.Button(
                        text(player, "rift.menu.home-set"),
                        text(player, "rift.menu.home-set-lore", placeholders),
                        List.of(),
                        RiftIcon.SET_HERE,
                        false,
                        act(context, current -> {
                            module.menu().setHome(player, context.slot(), home);
                            home(context, home);
                        })),
                new RiftScreen.Button(
                        text(player, "rift.menu.home-delete"),
                        text(player, "rift.menu.home-delete-lore", placeholders),
                        List.of(),
                        RiftIcon.DELETE,
                        false,
                        act(context, current -> confirmDelete(context, home))),
                new RiftScreen.Button(
                        text(player, "rift.screen.rename"),
                        text(player, "rift.screen.rename-detail"),
                        List.of(),
                        RiftIcon.RENAME,
                        false,
                        act(context, current -> rename(context, home))));
        context.renderer()
                .show(
                        player,
                        new RiftScreen(
                                text(player, "rift.screen.home-named", Map.of("name", name)),
                                null,
                                List.of(place(player, location)),
                                buttons,
                                1,
                                backTo(context, () -> homes(context))));
    }

    /** Asks for the home's new name; an empty one brings back the default name. */
    private void rename(Context context, int home) {
        Player player = context.player();
        ItemStack gun = context.gun();
        String name = RiftNames.home(gun, home, player);
        String given = RiftItems.homeName(gun, home);
        context.renderer()
                .prompt(
                        player,
                        new RiftPrompt(
                                text(player, "rift.screen.rename-title", Map.of("name", name)),
                                List.of(text(player, "rift.screen.rename-info")),
                                text(player, "rift.screen.rename-label"),
                                given == null ? "" : given,
                                RiftMenu.MAX_HOME_NAME,
                                text(player, "rift.screen.save"),
                                text(player, "rift.screen.cancel"),
                                entered -> act(context, current -> {
                                            module.menu().renameHome(player, context.slot(), home, entered);
                                            home(context, home);
                                        })
                                        .run(),
                                act(context, current -> home(context, home))));
    }

    private void confirmDelete(Context context, int home) {
        Player player = context.player();
        Map<String, String> placeholders = Map.of("home", String.valueOf(home + 1));
        List<RiftScreen.Button> buttons = List.of(
                RiftScreen.Button.of(
                        text(player, "rift.menu.delete-confirm", placeholders), RiftIcon.DELETE, act(context, gun -> {
                            RiftItems.setHome(gun, home, null);
                            if (RiftDestination.home(home).equals(RiftItems.destination(gun))) {
                                RiftItems.setDestination(gun, RiftDestination.back(), module.settings());
                            }
                            player.getInventory().setItem(context.slot(), gun);
                            player.playSound(player, Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.2f);
                            player.sendMessage(text(player, "rift.home-deleted", placeholders));
                            homes(context);
                        })),
                RiftScreen.Button.of(
                        text(player, "rift.menu.delete-cancel"),
                        RiftIcon.KEEP,
                        act(context, gun -> home(context, home))));
        context.renderer()
                .show(
                        player,
                        new RiftScreen(
                                text(player, "rift.screen.delete-title", placeholders),
                                null,
                                List.of(text(player, "rift.screen.delete-info")),
                                buttons,
                                2,
                                null));
    }

    /** The rift atlas: every structure kind, coloured by its dimension. */
    private void atlas(Context context) {
        Player player = context.player();
        ItemStack gun = context.gun();
        if (!RiftItems.isAttuned(gun)) {
            main(context);
            return;
        }
        RiftDestination selected = RiftItems.destination(gun);
        List<RiftScreen.Button> buttons = new ArrayList<>();
        for (RiftStructure structure : RiftStructure.values()) {
            String dimension = text(
                    player, "rift.dimension." + structure.environment().name().toLowerCase());
            Map<String, String> placeholders =
                    Map.of("structure", RiftNames.structure(structure.id(), player), "dimension", dimension);
            String colour =
                    switch (structure.environment()) {
                        case NETHER -> "§c";
                        case THE_END -> "§d";
                        default -> "§a";
                    };
            buttons.add(destination(
                    context,
                    colour + RiftNames.structure(structure.id(), player),
                    dimension,
                    lines(player, "rift.menu.structure-lore", placeholders),
                    RiftIcon.structure(structure),
                    RiftDestination.structure(structure),
                    selected));
        }
        context.renderer()
                .show(
                        player,
                        new RiftScreen(
                                text(player, "rift.screen.atlas-title"),
                                null,
                                List.of(
                                        text(
                                                player,
                                                "rift.screen.compass-active",
                                                Map.of("time", RiftItems.remaining(gun))),
                                        text(player, "rift.screen.atlas-info")),
                                buttons,
                                2,
                                backTo(context, () -> main(context))));
    }

    /** Closes the window and opens the tank chest; its back button returns here. */
    private void openTank(Context context) {
        Player player = context.player();
        context.renderer().close(player);
        module.menu()
                .openTank(
                        player,
                        context.slot(),
                        () ->
                                // a Bedrock client needs a moment after closing a chest before it shows a form
                                Bukkit.getScheduler()
                                        .runTaskLater(
                                                module.plugin(),
                                                () -> {
                                                    if (player.isOnline() && context.gun() != null) {
                                                        main(context);
                                                    }
                                                },
                                                BedrockSupport.isBedrock(player) ? 10L : 1L));
    }

    private RiftScreen.Button destination(
            Context context,
            String label,
            String detail,
            List<String> tooltip,
            RiftIcon icon,
            RiftDestination destination,
            RiftDestination selected) {
        Player player = context.player();
        boolean current = destination.equals(selected);
        List<String> lines = new ArrayList<>(tooltip);
        lines.add(0, text(player, current ? "rift.menu.current" : "rift.menu.click-select"));
        return new RiftScreen.Button(
                label, detail, lines, icon, current, act(context, gun -> select(context, destination)));
    }

    /** Makes the destination the gun's target and closes the window: the next attack opens the rift. */
    private void select(Context context, RiftDestination destination) {
        Player player = context.player();
        ItemStack gun = context.gun();
        RiftItems.setDestination(gun, destination, module.settings());
        player.getInventory().setItem(context.slot(), gun);
        context.renderer().close(player);
        player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        String ready = text(
                player, "rift.destination-ready", Map.of("destination", RiftNames.describe(destination, player, gun)));
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(TextComponent.fromLegacyText(ready)));
    }

    private RiftScreen.Button backTo(Context context, Runnable target) {
        return RiftScreen.Button.of(
                text(context.player(), "rift.screen.back"), null, act(context, gun -> target.run()));
    }

    /**
     * Wraps a button action: it only runs while the gun is still in its slot (otherwise the window just closes), with
     * a click sound, and the arm swing a Bedrock client sends after a form is not taken for an attack.
     */
    private Runnable act(Context context, Consumer<ItemStack> action) {
        return () -> {
            Player player = context.player();
            if (!player.isOnline()) {
                return;
            }
            module.gunListener().swingFree(player);
            ItemStack gun = context.gun();
            if (gun == null) {
                context.renderer().close(player);
                return;
            }
            player.playSound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
            action.accept(gun);
        };
    }

    private String place(Player player, Location location) {
        return location == null
                ? text(player, "rift.screen.not-available")
                : text(player, "rift.screen.coordinates", coordinates(location));
    }

    private static Map<String, String> coordinates(Location location) {
        if (location == null || location.getWorld() == null) {
            return Map.of();
        }
        return Map.of(
                "world", RiftNames.world(location.getWorld().getName(), null),
                "x", String.valueOf(location.getBlockX()),
                "y", String.valueOf(location.getBlockY()),
                "z", String.valueOf(location.getBlockZ()));
    }

    private static String text(Player player, String key) {
        return MessageManager.getMessage(player, key);
    }

    private static String text(Player player, String key, Map<String, String> placeholders) {
        return MessageManager.getMessage(player, key, placeholders);
    }

    private static List<String> lines(Player player, String key, Map<String, String> placeholders) {
        List<String> lines = new ArrayList<>();
        for (String line : MessageManager.getMessage(player, key, placeholders).split("\n")) {
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }
}
