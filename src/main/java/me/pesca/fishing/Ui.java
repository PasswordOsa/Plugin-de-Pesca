package me.pesca.fishing;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Utilidades de diseño para los menus. */
public final class Ui {
    private Ui() {}

    public static ItemStack item(Material m, String name, String... lore) {
        return item(m, name, Arrays.asList(lore));
    }

    public static ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(PescaPlugin.color("&r" + name));
        if (!lore.isEmpty()) {
            List<String> l = new ArrayList<>(lore.size());
            for (String s : lore) l.add(PescaPlugin.color("&r" + s));
            meta.setLore(l);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack glow(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack pane(Material m) {
        return item(m, " ");
    }

    /** Barra de progreso: ▰▰▰▱▱ */
    public static String bar(double frac, int len) {
        int on = (int) Math.round(Math.max(0.0, Math.min(1.0, frac)) * len);
        return "&b" + "▰".repeat(on) + "&8" + "▱".repeat(len - on);
    }
}
