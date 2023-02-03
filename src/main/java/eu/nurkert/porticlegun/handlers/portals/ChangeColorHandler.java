package eu.nurkert.porticlegun.handlers.portals;

import eu.nurkert.porticlegun.builders.BannerBuilder;
import eu.nurkert.porticlegun.builders.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ChangeColorHandler {

    final ItemStack up = new BannerBuilder().setName("§f▲")
            .setPatterns(new Pattern(DyeColor.GRAY, PatternType.DIAGONAL_LEFT),
                    new Pattern(DyeColor.GRAY, PatternType.DIAGONAL_RIGHT_MIRROR),
                    new Pattern(DyeColor.GRAY, PatternType.BORDER))
            .hideItemFlags().build();
    final ItemStack down = new BannerBuilder().setName("§f▼")
            .setPatterns(new Pattern(DyeColor.GRAY, PatternType.DIAGONAL_RIGHT),
                    new Pattern(DyeColor.GRAY, PatternType.DIAGONAL_LEFT_MIRROR),
                    new Pattern(DyeColor.GRAY, PatternType.BORDER))
            .hideItemFlags().build();


    private void changeColor() {
        // TODO Auto-generated method stub
        Inventory inv = Bukkit.createInventory(null, InventoryType.DROPPER,
                "§8PortalGun §7- §eChange Color");
        inv.setItem(0, up);
        inv.setItem(2, up);
        inv.setItem(6, down);
        inv.setItem(8, down);

        //inv.setItem(3, gun.getPrimaryPortalColor().getRepresentor());
        //inv.setItem(5, gun.getSecondaryPortalColor().getRepresentor());

        for (int i = 1; i <= 3; i++)
            inv.setItem((i * 3) - 2,
                    new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName("§0|").build());

        inv.setItem(4, new ItemBuilder(Material.BARRIER).setName("§c§lRESET").setLore("§8§o(requires double click)", "§7removes both portals").build());

        //player.openInventory(inv);
        //SoundHandler.playSound(event.getPlayer(), APGSound.INV_OPEN);
    }
}
