package me.pesca.fishing;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class PescaListener implements Listener {
    private static final Set<Material> VANILLA_FISH =
            EnumSet.of(Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH);

    private final PescaPlugin plugin;
    private final FishItems items;

    public PescaListener(PescaPlugin plugin) {
        this.plugin = plugin;
        this.items = plugin.items();
    }

    // ------------------------------------------------------------ datos de jugador

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        plugin.data().get(e.getUniqueId()); // lectura de disco fuera del hilo principal
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.data().get(e.getPlayer().getUniqueId());
        plugin.refreshInventory(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.guiViewers().remove(e.getPlayer().getUniqueId());
        plugin.data().unload(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------ pesca

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(e.getCaught() instanceof Item drop)) return;
        if (!VANILLA_FISH.contains(drop.getItemStack().getType())) return; // deja tesoros y basura
        Player p = e.getPlayer();
        if (!p.hasPermission("pesca.use")) return;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (rnd.nextDouble() < plugin.vanillaLootChance()) return;

        PlayerData d = plugin.data().get(p.getUniqueId());
        FishType t = plugin.registry().pick(d.level, rnd);
        if (t == null) return;

        double w = t.minWeight() + rnd.nextDouble() * (t.maxWeight() - t.minWeight());
        w = Math.round(w * 100.0) / 100.0;
        drop.setItemStack(items.create(t, w, p.getName()));

        int count = d.caught.merge(t.id(), 1, Integer::sum);
        Double rec = d.record.get(t.id());
        if (rec == null || w > rec) d.record.put(t.id(), w);
        d.totalCaught++;
        d.dirty = true;

        p.sendMessage(plugin.msg("atrapado", "color", t.color(), "pez", t.name(),
                "peso", String.format(Locale.US, "%.2f", w), "xp", t.xp()));
        if (count == 1) p.sendMessage(plugin.msg("nuevo-pez", "pez", t.name()));
        plugin.addXp(p, d, t.xp());
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
        plugin.guiViewers().remove(e.getPlayer().getUniqueId());
        Inventory inv = e.getInventory();
        if (isFridgeInv(inv)) freezeAll(inv);
    }

    /** Antes de romper: descongelar el contenido para que no quede congelado fuera de la heladera. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!isFridge(b)) return;
        if (b.getState() instanceof Container c) thawAll(c.getInventory());
    }

    /** El barril que se dropea conserva la marca de heladera. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        if (!(e.getBlockState() instanceof TileState ts)) return;
        if (!ts.getPersistentDataContainer().has(items.kFridge, PersistentDataType.BYTE)) return;
        for (Item it : e.getItems()) {
            if (it.getItemStack().getType() == Material.BARREL) it.setItemStack(items.fridgeItem(1));
        }
    }

    /** Las heladeras son inmunes a explosiones (evita perder la marca y el contenido). */
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::isFridge);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::isFridge);
    }

    /** Sin tolvas: evita sacar/meter peces sin pasar por apertura/cierre. */
    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        Inventory src = e.getSource(), dst = e.getDestination();
        if ((src.getType() == InventoryType.BARREL && isFridgeInv(src))
                || (dst.getType() == InventoryType.BARREL && isFridgeInv(dst))) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ GUI coleccion

    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        if (plugin.guiViewers().contains(e.getWhoClicked().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onGuiDrag(InventoryDragEvent e) {
        if (plugin.guiViewers().contains(e.getWhoClicked().getUniqueId())) e.setCancelled(true);
    }
}
