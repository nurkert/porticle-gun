package eu.nurkert.porticlegun.builders;

import java.util.Arrays;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.Pattern;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;

public class BannerBuilder {

    ItemStack item;

    public BannerBuilder() {
        item = new ItemStack(Material.WHITE_BANNER);
    }

    public BannerBuilder setName(String name) {
        ItemMeta meta = getItemMeta();
        meta.setDisplayName("§r"+name);
        item.setItemMeta(meta);
        return this;
    }

    public BannerBuilder setBaseColor(DyeColor color) {
        ItemMeta cache = item.getItemMeta();
        item = new ItemStack(Material.valueOf(color.toString() + "_BANNER"));
        item.setItemMeta(cache);
        return this;
    }

    public BannerBuilder setPatterns(Pattern... patterns) {
        BannerMeta meta = getItemMeta();
        meta.setPatterns(Arrays.asList(patterns));
        item.setItemMeta(meta);
        return this;
    }

    public BannerBuilder hideItemFlags() {
        ItemMeta meta = getItemMeta();
        for(ItemFlag flag : ItemFlag.values()) {
            meta.addItemFlags(flag);
        }
        item.setItemMeta(meta);
        return this;
    }

    public BannerBuilder setLore(String... lore) {
        ItemMeta meta = getItemMeta();
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return this;
    }

    public BannerBuilder setAmount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemStack build() {
        return item;
    }

    private BannerMeta getItemMeta() {
        return (BannerMeta) item.getItemMeta();
    }
}
