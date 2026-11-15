package me.pesca.fishing;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/** Coleccion paginada con filtro por rareza (clic izquierdo/derecho en la brujula). */
public final class CollectionMenu extends Menu {
    private static final int[] GRID = grid();

    private static int[] grid() {
        int[] g = new int[28];
        int i = 0;
        for (int r = 1; r <= 4; r++) for (int c = 1; c <= 7; c++) g[i++] = r * 9 + c;
        return g;
    }

    private int page = 0;
    private int filter = 0; // 0 = todas

    public CollectionMenu(PescaPlugin plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String title() {
        return "&8» &b&lPESCA &8| &fColección";
    }

    @Override
    protected int rows() {
        return 6;
    }

    private List<FishType> filtered() {
        List<FishType> l = new ArrayList<>();
        List<String> rar = plugin.registry().rarityNames();
        for (FishType t : plugin.registry().all()) {
            if (filter == 0 || (filter - 1 < rar.size() && t.rarity().equals(rar.get(filter - 1)))) l.add(t);
        }
        l.sort(Comparator.comparingInt(FishType::minLevel).thenComparingDouble(FishType::price));
        return l;
    }

    @Override
    protected void build() {
        border(Material.CYAN_STAINED_GLASS_PANE);
        for (int s : new int[]{0, 8, 45, 53}) set(s, Ui.pane(Material.BLUE_STAINED_GLASS_PANE));
        PlayerData d = plugin.data().get(player.getUniqueId());

        List<FishType> list = filtered();
        int pages = Math.max(1, (list.size() + GRID.length - 1) / GRID.length);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < GRID.length; i++) {
            int idx = page * GRID.length + i;
            if (idx >= list.size()) break;
            set(GRID[i], fishItem(list.get(idx), d));
        }

        if (page > 0) {
            set(45, Ui.item(Material.ARROW, "&e« Anterior", "&7Página " + page + "/" + pages), e -> {
                page--;
                redraw();
            });
        }
        if (page < pages - 1) {
            set(53, Ui.item(Material.ARROW, "&eSiguiente »", "&7Página " + (page + 2) + "/" + pages), e -> {
                page++;
                redraw();
            });
        }

        List<String> rar = plugin.registry().rarityNames();
        String name = filter == 0 ? "Todas" : rar.get(filter - 1);
        set(47, Ui.item(Material.COMPASS, "&b&lFiltro: &f" + name,
                "&7Clic izquierdo: siguiente", "&7Clic derecho: anterior"), e -> {
            int n = rar.size() + 1;
            filter = e.isRightClick() ? (filter + n - 1) % n : (filter + 1) % n;
            page = 0;
            redraw();
        });

        int found = d.caught.size(), total = plugin.registry().all().size();
        set(51, Ui.item(Material.NAUTILUS_SHELL, "&b&lProgreso",
                "&7Descubiertas: &f" + found + "&8/&f" + total,
                Ui.bar(total == 0 ? 0 : (double) found / total, 14)));
        back();
        fill(Material.BLACK_STAINED_GLASS_PANE);
    }

    private ItemStack fishItem(FishType t, PlayerData d) {
        String zones = t.zoneMask() == (1 << Zone.values().length) - 1 ? "Cualquiera" : zoneNames(t);
        String time = t.time() == 1 ? "Día" : t.time() == 2 ? "Noche" : "Cualquiera";
        String weather = t.weather() == 1 ? "Lluvia" : t.weather() == 2 ? "Despejado" : "Cualquiera";

        Integer count = d.caught.get(t.id());
        if (count == null) {
            return Ui.item(Material.GRAY_DYE, "&8??? &7(" + t.color() + t.rarity() + "&7)",
                    "&7Aún no lo has pescado.", "&7",
                    "&7Nivel mínimo: &f" + t.minLevel(),
                    "&7Zonas: &f" + zones,
                    "&7Horario: &f" + time + " &8| &7Clima: &f" + weather);
        }
        List<String> lore = new ArrayList<>();
        if (!t.desc().isEmpty()) {
            lore.add("&7&o" + t.desc());
            lore.add("&7");
        }
        lore.add("&7Rareza: " + t.color() + t.rarity());
        lore.add("&7Nivel mínimo: &f" + t.minLevel());
        lore.add("&7Zonas: &f" + zones);
        lore.add("&7Horario: &f" + time + " &8| &7Clima: &f" + weather);
        lore.add("&7Tamaño: &f" + fmt(t.minWeight()) + " - " + fmt(t.maxWeight()) + " kg");
        lore.add("&7Valor base: &a" + plugin.economy().format(t.price()));
        lore.add("&7");
        lore.add("&7Capturados: &f" + count);
        Double rec = d.record.get(t.id());
        lore.add("&7Tu récord: &e" + fmt(rec == null ? 0 : rec) + " kg");
        ServerRecords.Rec sr = plugin.records().get(t.id());
        lore.add("&7Récord del servidor: " + (sr == null ? "&8-" : "&6" + fmt(sr.weight()) + " kg &7(" + sr.holder() + ")"));
        ItemStack it = Ui.item(t.material(), t.color() + "&l" + t.name(), lore);
        return t.rank() >= 3 ? Ui.glow(it) : it;
    }

    private String zoneNames(FishType t) {
        StringJoiner sj = new StringJoiner(", ");
        for (Zone z : Zone.values()) if (t.inZone(z)) sj.add(z.display());
        return sj.toString();
    }

    private String fmt(double v) {
        return String.format(Locale.US, "%.2f", v);
    }
}
