package eu.nurkert.porticlegun.commands;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.builders.ItemBuilder;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.rift.RiftGunListener;
import eu.nurkert.porticlegun.util.BedrockSupport;
import eu.nurkert.porticlegun.util.GuiGlyphs;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The menu opened by {@code /porticlegun}: the plugin's items to take out like from a creative inventory (click,
 * shift-click, number key or drop key, as often as wanted), a crafting table view of each recipe, and for admins the
 * portal management ({@link AdminMenu}). Everyone may take the PorticleGun; the other items and the ingredients only
 * admins, everyone else sees how they are crafted. Java players with the resource pack get drawn backgrounds
 * ({@link MenuGui}), everyone else the same slots between glass panes.
 */
public class GunMenu implements Listener {

    private static final int SIZE = 27;

    private final AdminMenu admin;
    private final Predicate<Player> resourcePack;

    public GunMenu(AdminMenu admin, Predicate<Player> resourcePack) {
        this.admin = admin;
        this.resourcePack = resourcePack;
    }

    /** One open page: what each slot hands out or does. */
    private static final class Holder implements InventoryHolder {
        private final Map<Integer, Supplier<ItemStack>> items = new HashMap<>();
        private final Map<Integer, Runnable> buttons = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private boolean drawn(Player player) {
        return !BedrockSupport.isBedrock(player) && resourcePack.test(player);
    }

    public void open(Player player) {
        boolean isAdmin = player.hasPermission(Permissions.ADMIN);
        List<RecipeBook.Entry> entries =
                RecipeBook.entries(PorticleGun.getInstance().getRiftModule());
        boolean drawn = drawn(player);
        boolean single = entries.size() == 1;
        String title = MessageManager.getMessage(player, "menus.porticlegun.title");
        if (drawn) {
            char background = single
                    ? (isAdmin ? MenuGui.SINGLE_ADMIN : MenuGui.SINGLE)
                    : (isAdmin ? MenuGui.OVERVIEW_ADMIN : MenuGui.OVERVIEW);
            title = GuiGlyphs.chestTitle(background, title);
        }
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.inventory = inventory;

        int[] columns = single ? new int[] {4} : MenuGui.COLUMNS;
        for (int i = 0; i < entries.size() && i < columns.length; i++) {
            RecipeBook.Entry entry = entries.get(i);
            int slot = columns[i];
            boolean takeable = entry.freeForAll() || isAdmin;
            ItemStack shown = entry.item().get();
            inventory.setItem(
                    slot,
                    withLine(
                            shown,
                            MessageManager.getMessage(
                                    player, takeable ? "menus.porticlegun.take" : "menus.porticlegun.craft-only")));
            if (takeable) {
                holder.items.put(slot, entry.item());
            }
            if (entry.grid() != null) {
                String name = plainName(shown);
                inventory.setItem(
                        slot + 9,
                        new ItemBuilder(Material.CRAFTING_TABLE)
                                .setName(MessageManager.getMessage(
                                        player, "menus.porticlegun.recipe-button", Map.of("item", name)))
                                .setLore(MessageManager.getMessage(player, "menus.porticlegun.recipe-hint"))
                                .build());
                holder.buttons.put(slot + 9, () -> openRecipe(player, entry, name));
            }
        }
        if (isAdmin) {
            inventory.setItem(
                    MenuGui.ADMIN_SLOT,
                    new ItemBuilder(Material.COMMAND_BLOCK)
                            .setName(MessageManager.getMessage(player, "menus.porticlegun.admin-button"))
                            .setLore(MessageManager.getMessage(player, "menus.porticlegun.admin-hint"))
                            .build());
            holder.buttons.put(MenuGui.ADMIN_SLOT, () -> admin.open(player));
        }
        if (!drawn) {
            fill(inventory, player);
        }
        player.openInventory(inventory);
    }

    /** The recipe as a crafting table: the grid, the result, and back to the overview. */
    private void openRecipe(Player player, RecipeBook.Entry entry, String name) {
        boolean drawn = drawn(player);
        String title = MessageManager.getMessage(player, "menus.porticlegun.recipe-title", Map.of("item", name));
        Holder holder = new Holder();
        Inventory inventory =
                Bukkit.createInventory(holder, SIZE, drawn ? GuiGlyphs.chestTitle(MenuGui.RECIPE, title) : title);
        holder.inventory = inventory;
        // the ingredients and the result can be taken like in the overview, but only by admins
        boolean takeable = player.hasPermission(Permissions.ADMIN);

        for (int i = 0; i < MenuGui.GRID.length; i++) {
            Supplier<ItemStack> ingredient = entry.grid().get(i);
            if (ingredient == null) {
                continue;
            }
            inventory.setItem(MenuGui.GRID[i], ingredient.get());
            if (takeable) {
                holder.items.put(MenuGui.GRID[i], ingredient);
            }
        }
        Supplier<ItemStack> result = () -> {
            ItemStack item = entry.item().get();
            item.setAmount(1);
            return item;
        };
        ItemStack shown = result.get();
        if (entry.shapeless()) {
            shown = withLine(shown, MessageManager.getMessage(player, "menus.porticlegun.shapeless"));
        }
        if (needsRiftPermission(entry) && !player.hasPermission(RiftGunListener.PERMISSION)) {
            shown = withLine(shown, MessageManager.getMessage(player, "menus.porticlegun.no-permission"));
        }
        inventory.setItem(MenuGui.RESULT, shown);
        if (takeable) {
            holder.items.put(MenuGui.RESULT, result);
        }

        String back = MessageManager.getMessage(player, "menus.porticlegun.back");
        inventory.setItem(
                MenuGui.BACK,
                drawn
                        ? MenuGui.blank(back)
                        : new ItemBuilder(Material.ARROW).setName(back).build());
        holder.buttons.put(MenuGui.BACK, () -> open(player));
        if (!drawn) {
            // panes around the grid and the result, which stay free like on a crafting table
            fill(inventory, player, MenuGui.GRID, MenuGui.RESULT);
        }
        player.openInventory(inventory);
    }

    /** The interdimensional gun and the rift compass are crafted only with the rift permission (see RiftRecipes). */
    private static boolean needsRiftPermission(RecipeBook.Entry entry) {
        return !entry.shapeless() && !entry.freeForAll();
    }

    private static void fill(Inventory inventory, Player player) {
        fill(inventory, player, new int[0], -1);
    }

    private static void fill(Inventory inventory, Player player, int[] free, int alsoFree) {
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setName(MessageManager.getMessage(player, "menus.porticlegun.filler-name"))
                .build();
        for (int i = 0; i < inventory.getSize(); i++) {
            int slot = i;
            if (inventory.getItem(i) == null
                    && i != alsoFree
                    && Arrays.stream(free).noneMatch(f -> f == slot)) {
                inventory.setItem(i, filler);
            }
        }
    }

    private static String plainName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? ChatColor.stripColor(meta.getDisplayName()) : "";
    }

    /** The item with one more line below its lore. */
    private static ItemStack withLine(ItemStack item, String line) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(line);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void on(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        boolean menu = slot >= 0 && slot < SIZE;
        // the menu itself never changes; the player's inventory works as usual, but nothing goes into the menu
        if (menu || event.isShiftClick() || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
        }
        if (!menu) {
            return;
        }
        Runnable button = holder.buttons.get(slot);
        if (button != null) {
            player.playSound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
            button.run();
            return;
        }
        Supplier<ItemStack> item = holder.items.get(slot);
        if (item != null && take(event, player, item.get())) {
            player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.3f);
        }
    }

    /**
     * Hands out a fresh copy of a slot's item the way the click asks for it, like a creative inventory: onto the
     * cursor (or onto the stack already there), into the inventory with shift, into a hotbar slot with its number
     * key, or thrown with the drop key.
     *
     * @return whether the player got something
     */
    private static boolean take(InventoryClickEvent event, Player player, ItemStack fresh) {
        switch (event.getClick()) {
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                for (ItemStack leftover : player.getInventory().addItem(fresh).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
                return true;
            }
            case NUMBER_KEY -> {
                int hotbar = event.getHotbarButton();
                if (hotbar < 0 || player.getInventory().getItem(hotbar) != null) {
                    return false;
                }
                player.getInventory().setItem(hotbar, fresh);
                return true;
            }
            case DROP, CONTROL_DROP -> {
                if (event.getClick() == ClickType.DROP) {
                    fresh.setAmount(1);
                }
                Item dropped = player.getWorld().dropItem(player.getEyeLocation(), fresh);
                dropped.setVelocity(player.getLocation().getDirection().multiply(0.3));
                dropped.setPickupDelay(30);
                return true;
            }
            case LEFT, RIGHT, MIDDLE -> {
                ItemStack cursor = event.getCursor();
                ItemStack onCursor;
                if (cursor == null || cursor.getType().isAir()) {
                    onCursor = fresh;
                } else if (cursor.isSimilar(fresh) && cursor.getAmount() < cursor.getMaxStackSize()) {
                    onCursor = cursor.clone();
                    onCursor.setAmount(Math.min(cursor.getMaxStackSize(), cursor.getAmount() + fresh.getAmount()));
                } else {
                    return false;
                }
                // set after the cancelled click is through, or the client's own cursor update wins
                Bukkit.getScheduler().runTask(PorticleGun.getInstance(), () -> {
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
                        player.setItemOnCursor(onCursor);
                    }
                });
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @EventHandler
    public void on(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < SIZE) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
