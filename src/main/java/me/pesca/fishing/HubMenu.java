package me.pesca.fishing;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HubMenu extends Menu {
    public HubMenu(PescaPlugin plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String title() {
        return "&8» &b&lPESCA &8| &fMenú principal";
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void build() {
        border(Material.CYAN_STAINED_GLASS_PANE);
        for (int s : new int[]{0, 8, 45, 53}) set(s, Ui.pane(Material.BLUE_STAINED_GLASS_PANE));

        PlayerData d = plugin.data().get(player.getUniqueId());
        plugin.quests().ensure(d);
        set(13, profile(d));

        int found = d.caught.size(), total = plugin.registry().all().size();
        set(29, Ui.item(Material.NAUTILUS_SHELL, "&b&lColección",
                        "&7Todas las especies del mar.", "&7",
                        "&7Descubiertas: &f" + found + "&8/&f" + total,
                        Ui.bar(total == 0 ? 0 : (double) found / total, 12), "&7", "&eClic para abrir"),
                e -> new CollectionMenu(plugin, player).open());

        Market.Preview pv = plugin.market().preview(player);
        set(31, Ui.item(Material.EMERALD, "&a&lMercado",
                        "&7Vende tus capturas. Los precios", "&7suben y bajan con la demanda.", "&7",
                        "&7Peces frescos: &f" + pv.sellable(),
                        "&7Valor estimado: &a" + plugin.economy().format(pv.estimate()), "&7", "&eClic para abrir"),
                e -> new MarketMenu(plugin, player).open());

        double bal = plugin.economy().balance(player, d);
        set(33, Ui.item(Material.FISHING_ROD, "&6&lTienda del pescador",
                        "&7Cañas y carnadas para pescar mejor.", "&7",
                        "&7Tu saldo: &a" + plugin.economy().format(bal), "&7", "&eClic para abrir"),
                e -> new ShopMenu(plugin, player).open());

        int done = 0, n = plugin.quests().today().size();
        for (int i = 0; i < n; i++) if (plugin.quests().isClaimed(d, i)) done++;
        set(38, Ui.item(Material.WRITABLE_BOOK, "&d&lMisiones diarias",
                        "&7Completa retos y gana dinero y XP.", "&7",
                        "&7Reclamadas hoy: &f" + done + "&8/&f" + n, "&7", "&eClic para abrir"),
                e -> new QuestMenu(plugin, player).open());

        List<String> tl = new ArrayList<>();
        tl.add("&7Gana quien pesque más kilos.");
        tl.add("&7");
        tl.addAll(plugin.tournament().lines());
        set(40, plugin.tournament().active()
                ? Ui.glow(Ui.item(Material.CLOCK, "&e&lTorneo &a(EN CURSO)", tl))
                : Ui.item(Material.CLOCK, "&e&lTorneo", tl));

        set(42, Ui.item(Material.BARREL, "&b&l❄ Heladera",
                "&7Los peces se dañan con el tiempo.", "&7Guárdalos en una heladera cerrada",
                "&7y no envejecerán.", "&7",
                "&7Receta: &f7 hierro + barril + hielo compacto"));

        set(49, Ui.item(Material.BARRIER, "&cCerrar"), e -> player.closeInventory());
        fill(Material.BLACK_STAINED_GLASS_PANE);
    }

    private ItemStack profile(PlayerData d) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(player);
        sm.setDisplayName(PescaPlugin.color("&r&b&l" + player.getName()));

        long need = plugin.xpToNext(d.level);
        List<String> lore = new ArrayList<>();
        lore.add("&7Nivel &f" + d.level + "&8/&7" + plugin.maxLevel());
        if (need == 0) {
            lore.add("&6★ NIVEL MÁXIMO");
        } else {
            lore.add(Ui.bar((double) d.xp / need, 16) + " &f" + Math.round(100.0 * d.xp / need) + "%");
            lore.add("&7XP &f" + d.xp + "&8/&f" + need);
        }
        lore.add("&7");
        lore.add("&7Peces: &f" + d.totalCaught + " &8| &7Perfectas: &e" + d.perfect);
        lore.add("&7Ganado: &a" + plugin.economy().format(d.totalEarned));
        if (plugin.economy().internal()) lore.add("&7Saldo: &a" + plugin.economy().format(d.balance));
        lore.add("&7");
        lore.add("&b&lCondiciones aquí");
        var loc = player.getLocation();
        long t = loc.getWorld().getTime();
        boolean night = t >= 13000 && t < 23000;
        lore.add("&7Zona: &f" + Zone.at(loc).display());
        lore.add("&7Hora: &f" + (night ? "Noche" : "Día") + " &8| &7Clima: &f" + (loc.getWorld().hasStorm() ? "Lluvia" : "Despejado"));

        List<String> colored = new ArrayList<>(lore.size());
        for (String s : lore) colored.add(PescaPlugin.color("&r" + s));
        sm.setLore(colored);
        head.setItemMeta(sm);
        return head;
    }
}
