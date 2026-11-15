package me.pesca.fishing;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Base de todos los menus. Los clics se resuelven por slot (sin buscar en listas). */
public abstract class Menu implements InventoryHolder {
    protected final PescaPlugin plugin;
    protected final Player player;
    protected Inventory inv;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Menu(PescaPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    protected abstract String title();

    protected abstract int rows();

    protected abstract void build();

    public final void open() {
        inv = Bukkit.createInventory(this, rows() * 9, PescaPlugin.color(title()));
        build();
        player.openInventory(inv);
        plugin.menus().put(player.getUniqueId(), this); // despues de abrir: el cierre del menu anterior ya paso
    }

    /** Redibuja sin cerrar ni reabrir (sin parpadeo). */
    protected final void redraw() {
        actions.clear();
        inv.clear();
        build();
    }

    protected final void set(int slot, ItemStack item) {
        inv.setItem(slot, item);
    }

    protected final void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inv.setItem(slot, item);
        actions.put(slot, action);
    }

    protected final void border(Material m) {
        ItemStack pane = Ui.pane(m);
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            int r = i / 9, c = i % 9;
            if (r == 0 || r == size / 9 - 1 || c == 0 || c == 8) inv.setItem(i, pane);
        }
    }

    protected final void fill(Material m) {
        ItemStack pane = Ui.pane(m);
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, pane);
    }

    protected final void back() {
        set(inv.getSize() - 5, Ui.item(Material.OAK_DOOR, "&e« Volver", "&7Menú principal"),
                e -> new HubMenu(plugin, player).open());
    }

    public final void handle(InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getClickedInventory() != inv) return;
        Consumer<InventoryClickEvent> a = actions.get(e.getSlot());
        if (a == null) return;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f);
        a.accept(e);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
