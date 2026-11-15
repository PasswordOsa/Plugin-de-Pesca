package me.pesca.fishing;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;

public final class CollectionGui {
    private CollectionGui() {}

    public static void open(PescaPlugin plugin, Player p, PlayerData d) {
        var all = plugin.registry().all();
        int n = Math.min(all.size(), 54);
        int size = Math.max(9, ((n + 8) / 9) * 9);
        int found = 0;
        for (FishType t : all) if (d.caught.containsKey(t.id())) found++;
        Inventory inv = Bukkit.createInventory(null, size,
                PescaPlugin.color("&b&lColección &7(" + found + "/" + all.size() + ")"));
        int slot = 0;
        for (FishType t : all) {
            if (slot >= 54) break;
            inv.setItem(slot++, build(t, d));
        }
        plugin.guiViewers().add(p.getUniqueId());
        p.openInventory(inv);
    }

    private static ItemStack build(FishType t, PlayerData d) {
        Integer count = d.caught.get(t.id());
        if (count == null) {
            ItemStack it = new ItemStack(Material.GRAY_DYE);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(PescaPlugin.color("&r&8???"));
            m.setLore(List.of(
                    PescaPlugin.color("&r&7Nivel mínimo: &f" + t.minLevel()),
                    PescaPlugin.color("&r&8Aún no lo has pescado")));
            it.setItemMeta(m);
            return it;
        }
        ItemStack it = new ItemStack(t.material());
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(PescaPlugin.color("&r" + t.color() + t.name()));
        Double rec = d.record.get(t.id());
        m.setLore(List.of(
                PescaPlugin.color("&r&7Rareza: " + t.color() + t.rarity()),
                PescaPlugin.color("&r&7Nivel mínimo: &f" + t.minLevel()),
                PescaPlugin.color("&r&7Valor base: &a" + String.format(Locale.US, "%.2f", t.price())),
                PescaPlugin.color("&r&7Capturados: &f" + count),
                PescaPlugin.color("&r&7Récord: &f" + String.format(Locale.US, "%.2f", rec == null ? 0.0 : rec) + " kg")));
        it.setItemMeta(m);
        return it;
    }
}
