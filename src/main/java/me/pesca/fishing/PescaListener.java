package me.pesca.fishing;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/** Datos de jugador, menus y heladera. (La pesca esta en FishingListener.) */
public final class PescaListener implements Listener {
    private final PescaPlugin plugin;
    private final FishItems items;

    public PescaListener(PescaPlugin plugin) {
        this.plugin = plugin;
        this.items = plugin.items();
    }

    // ------------------------------------------------------------ jugador

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        plugin.data().get(e.getUniqueId()); // lectura de disco fuera del hilo principal
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.data().get(e.getPlayer().getUniqueId());
        plugin.refreshInventory(e.getPlayer());
        plugin.tournament().join(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        plugin.menus().remove(id);
        plugin.fishing().cleanup(id);
        plugin.data().unload(id);
    }

    // ------------------------------------------------------------ menus

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        Menu m = plugin.menus().get(e.getWhoClicked().getUniqueId());
        if (m != null) m.handle(e);
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (plugin.menus().containsKey(e.getWhoClicked().getUniqueId())) e.setCancelled(true);
    }

    // ------------------------------------------------------------ heladera

    private boolean isFridge(Block b) {
        if (b.getType() != Material.BARREL) return false;
        return b.getState() instanceof TileState ts
                && ts.getPersistentDataContainer().has(items.kFridge, PersistentDataType.BYTE);
    }

    private boolean isFridgeInv(Inventory inv) {
        if (inv.getType() != InventoryType.BARREL) return false;
        Location loc = inv.getLocation();
        return loc != null && isFridge(loc.getBlock());
    }

    private void freezeAll(Inventory inv) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.hasItemMeta() && items.freeze(it, now)) inv.setItem(i, it);
        }
    }

    private void thawAll(Inventory inv) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.hasItemMeta() && items.thaw(it, now)) inv.setItem(i, it);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!items.isFridgeItem(e.getItemInHand())) return;
        if (e.getBlockPlaced().getState() instanceof TileState ts) {
            ts.getPersistentDataContainer().set(items.kFridge, PersistentDataType.BYTE, (byte) 1);
            ts.update();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        Inventory inv = e.getInventory();
        if (isFridgeInv(inv)) thawAll(inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        plugin.menus().remove(e.getPlayer().getUniqueId());
        Inventory inv = e.getInventory();
        if (isFridgeInv(inv)) freezeAll(inv);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!isFridge(b)) return;
        if (b.getState() instanceof Container c) thawAll(c.getInventory());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        if (!(e.getBlockState() instanceof TileState ts)) return;
        if (!ts.getPersistentDataContainer().has(items.kFridge, PersistentDataType.BYTE)) return;
        for (Item it : e.getItems()) {
            if (it.getItemStack().getType() == Material.BARREL) it.setItemStack(items.fridgeItem(1));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::isFridge);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::isFridge);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        Inventory src = e.getSource(), dst = e.getDestination();
        if ((src.getType() == InventoryType.BARREL && isFridgeInv(src))
                || (dst.getType() == InventoryType.BARREL && isFridgeInv(dst))) {
            e.setCancelled(true);
        }
    }
}
