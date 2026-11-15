package me.pesca.fishing;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class SellService {
    private final PescaPlugin plugin;

    public SellService(PescaPlugin plugin) {
        this.plugin = plugin;
    }

    public void sell(Player p, boolean handOnly) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        FishItems items = plugin.items();
        PlayerInventory inv = p.getInventory();
        long now = System.currentTimeMillis();
        int start = handOnly ? inv.getHeldItemSlot() : 0;
        int end = handOnly ? inv.getHeldItemSlot() : 35;

        double total = 0;
        int sold = 0, spoiled = 0;
        for (int i = start; i <= end; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || !it.hasItemMeta()) continue;
            FishItems.Info info = items.read(it, now);
            if (info == null) continue;
            if (info.state() == FishItems.State.SPOILED) {
                spoiled += it.getAmount();
                continue;
            }
            total += items.value(info, d.level) * it.getAmount();
            sold += it.getAmount();
            inv.setItem(i, null);
        }

        if (sold == 0) {
            p.sendMessage(plugin.msg("nada-para-vender"));
        } else {
            total = Math.round(total * 100.0) / 100.0;
            plugin.economy().deposit(p, d, total);
            d.totalEarned += total;
            d.dirty = true;
            p.sendMessage(plugin.msg("vendido", "cantidad", sold, "dinero", plugin.economy().format(total)));
        }
        if (spoiled > 0) p.sendMessage(plugin.msg("podridos-omitidos", "cantidad", spoiled));
    }

    /** Tira los peces podridos del inventario. */
    public void clean(Player p) {
        PlayerInventory inv = p.getInventory();
        long now = System.currentTimeMillis();
        int removed = 0;
        for (int i = 0; i <= 35; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || !it.hasItemMeta()) continue;
            FishItems.Info info = plugin.items().read(it, now);
            if (info != null && info.state() == FishItems.State.SPOILED) {
                removed += it.getAmount();
                inv.setItem(i, null);
            }
        }
        p.sendMessage(plugin.msg("limpiado", "cantidad", removed));
    }
}
