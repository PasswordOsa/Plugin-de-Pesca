package me.pesca.fishing;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ShopMenu extends Menu {
    private static final int[] ROD_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] BAIT_SLOTS = {29, 30, 31, 32, 33};

    public ShopMenu(PescaPlugin plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String title() {
        return "&8» &b&lPESCA &8| &6Tienda del pescador";
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void build() {
        border(Material.ORANGE_STAINED_GLASS_PANE);
        for (int s : new int[]{0, 8, 45, 53}) set(s, Ui.pane(Material.YELLOW_STAINED_GLASS_PANE));
        PlayerData d = plugin.data().get(player.getUniqueId());
        EconomyBridge eco = plugin.economy();

        set(4, Ui.item(Material.GOLD_INGOT, "&6&lTu saldo", "&a" + eco.format(eco.balance(player, d))));
        set(19, Ui.item(Material.FISHING_ROD, "&e&lCañas", "&7Más velocidad, suerte y control.", "&7Requieren nivel."));
        set(38, Ui.item(Material.BEETROOT_SEEDS, "&e&lCarnadas", "&7En la mano secundaria.", "&7Se gasta una por lanzamiento."));

        int i = 0;
        for (RodItems.RodDef rd : plugin.rods().rods().values()) {
            if (i >= ROD_SLOTS.length) break;
            boolean ok = d.level >= rd.level();
            List<String> lore = new ArrayList<>();
            lore.add("&b⚡ Velocidad: &f+" + Math.round(rd.speed() * 100) + "%");
            lore.add("&a☘ Suerte: &f+" + rd.luck());
            lore.add("&e◎ Control: &f+" + rd.control());
            lore.add("&7");
            lore.add((ok ? "&a✔" : "&c✘") + " &7Nivel requerido: &f" + rd.level());
            lore.add("&7Precio: &a" + eco.format(rd.price()));
            lore.add("&7");
            lore.add(ok ? "&eClic para comprar" : "&cNivel insuficiente");
            ItemStack it = Ui.item(Material.FISHING_ROD, rd.color() + "&l" + rd.name(), lore);
            set(ROD_SLOTS[i++], ok ? Ui.glow(it) : it, e -> buyRod(rd));
        }

        i = 0;
        for (RodItems.BaitDef bd : plugin.rods().baits().values()) {
            if (i >= BAIT_SLOTS.length) break;
            List<String> lore = new ArrayList<>();
            lore.add("&b⚡ Velocidad: &f+" + Math.round(bd.speed() * 100) + "%");
            lore.add("&a☘ Suerte: &f+" + bd.luck());
            lore.add("&e◎ Control: &f+" + bd.control());
            lore.add("&7");
            lore.add("&7Paquete: &f" + bd.pack() + " unidades");
            lore.add("&7Precio: &a" + eco.format(bd.price()));
            lore.add("&7");
            lore.add("&eClic para comprar");
            set(BAIT_SLOTS[i++], Ui.item(bd.material(), "&e&l" + bd.name(), lore), e -> buyBait(bd));
        }
        back();
        fill(Material.BLACK_STAINED_GLASS_PANE);
    }

    private void buyRod(RodItems.RodDef rd) {
        PlayerData d = plugin.data().get(player.getUniqueId());
        if (d.level < rd.level()) {
            player.sendMessage(plugin.msg("nivel-insuficiente", "nivel", rd.level()));
            return;
        }
        if (!plugin.economy().withdraw(player, d, rd.price())) {
            player.sendMessage(plugin.msg("sin-dinero"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        give(plugin.rods().rodItem(rd));
        bought(rd.name(), rd.price());
    }

    private void buyBait(RodItems.BaitDef bd) {
        PlayerData d = plugin.data().get(player.getUniqueId());
        if (!plugin.economy().withdraw(player, d, bd.price())) {
            player.sendMessage(plugin.msg("sin-dinero"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        give(plugin.rods().baitItem(bd, bd.pack()));
        bought(bd.name() + " x" + bd.pack(), bd.price());
    }

    private void give(ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
    }

    private void bought(String name, double price) {
        player.sendMessage(plugin.msg("comprado", "item", name, "precio", plugin.economy().format(price)));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
        redraw();
    }
}
