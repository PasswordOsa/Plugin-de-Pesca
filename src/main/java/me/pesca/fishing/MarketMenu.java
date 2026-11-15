package me.pesca.fishing;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class MarketMenu extends Menu {
    private static final int[] GRID = grid();

    private static int[] grid() {
        int[] g = new int[28];
        int i = 0;
        for (int r = 1; r <= 4; r++) for (int c = 1; c <= 7; c++) g[i++] = r * 9 + c;
        return g;
    }

    public MarketMenu(PescaPlugin plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String title() {
        return "&8» &b&lPESCA &8| &aMercado";
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void build() {
        border(Material.LIME_STAINED_GLASS_PANE);
        for (int s : new int[]{0, 8, 45, 53}) set(s, Ui.pane(Material.GREEN_STAINED_GLASS_PANE));
        PlayerData d = plugin.data().get(player.getUniqueId());
        Market market = plugin.market();
        Market.Preview pv = market.preview(player);

        List<FishType> list = new ArrayList<>(plugin.registry().all());
        list.sort(Comparator.comparingInt(FishType::minLevel).thenComparingDouble(FishType::price));
        for (int i = 0; i < GRID.length && i < list.size(); i++) {
            FishType t = list.get(i);
            double dem = market.demand(t);
            boolean trend = market.trending(t);
            double price = t.price() * (1.0 + plugin.sellBonusPerLevel() * (d.level - 1)) * market.multiplier(t);
            String arrow = trend ? "&6▲ En tendencia &a+" + Math.round((market.trendBoost() - 1) * 100) + "%"
                    : dem < 0.999 ? "&c▼ Saturado" : "&7• Estable";
            List<String> lore = new ArrayList<>();
            lore.add("&7Precio medio: &a" + plugin.economy().format(price));
            lore.add("&7Demanda: " + Ui.bar(dem, 10) + " &f" + Math.round(dem * 100) + "%");
            lore.add(arrow);
            lore.add("&7");
            lore.add("&7En tu inventario: &f" + pv.perSpecies().getOrDefault(t.id(), 0));
            ItemStack it = Ui.item(t.material(), t.color() + "&l" + t.name(), lore);
            set(GRID[i], trend ? Ui.glow(it) : it);
        }

        set(45, Ui.item(Material.ROTTEN_FLESH, "&2Tirar podridos",
                "&7Peces podridos: &c" + pv.spoiled(), "&7", "&eClic para tirarlos"), e -> {
            plugin.market().clean(player);
            redraw();
        });
        set(46, Ui.item(Material.HOPPER, "&aVender en mano", "&7Vende solo el pez que llevas", "&7en la mano."), e -> {
            plugin.market().sell(player, true);
            redraw();
        });
        set(53, Ui.glow(Ui.item(Material.EMERALD_BLOCK, "&a&lVENDER TODO",
                "&7Peces frescos: &f" + pv.sellable(),
                "&7Valor estimado: &a" + plugin.economy().format(pv.estimate()),
                "&8(cada pez vendido baja un poco el precio)", "&7", "&eClic para vender")), e -> {
            plugin.market().sell(player, false);
            redraw();
        });
        back();
        fill(Material.BLACK_STAINED_GLASS_PANE);
    }
}
