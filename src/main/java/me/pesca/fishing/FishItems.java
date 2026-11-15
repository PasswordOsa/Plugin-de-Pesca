package me.pesca.fishing;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Los peces son items con datos en PersistentDataContainer. La frescura es PEREZOSA:
 * se guarda un instante de caducidad absoluto, sin tareas por pez ni por tick.
 * Heladera: al cerrar el barril se "congela" (se guarda el instante), al abrirlo se
 * descongela desplazando la caducidad el tiempo que estuvo congelado.
 */
public final class FishItems {
    public enum State { FRESH, WARNING, SPOILED }

    public record Info(FishType type, double weight, State state) {}

    public final NamespacedKey kId, kWeight, kExpires, kFrozen, kSig, kCatcher, kFridge, kPerfect;
    private final FishRegistry registry;
    private final PescaPlugin plugin;
    private double warnFraction = 0.25;

    public FishItems(PescaPlugin plugin, FishRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        kId = new NamespacedKey(plugin, "fish_id");
        kWeight = new NamespacedKey(plugin, "fish_weight");
        kExpires = new NamespacedKey(plugin, "fish_expires");
        kFrozen = new NamespacedKey(plugin, "fish_frozen");
        kSig = new NamespacedKey(plugin, "fish_sig");
        kCatcher = new NamespacedKey(plugin, "fish_catcher");
        kFridge = new NamespacedKey(plugin, "fridge");
        kPerfect = new NamespacedKey(plugin, "fish_perfect");
    }

    public void reload(FileConfiguration cfg) {
        warnFraction = Math.min(1.0, Math.max(0.0, cfg.getDouble("frescura.aviso-porcentaje", 25) / 100.0));
    }

    // ---------------------------------------------------------------- crear

    public ItemStack create(FishType t, double weight, String catcher, boolean perfect) {
        ItemStack item = new ItemStack(t.material());
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        long now = System.currentTimeMillis();
        pdc.set(kId, PersistentDataType.STRING, t.id());
        pdc.set(kWeight, PersistentDataType.DOUBLE, weight);
        pdc.set(kExpires, PersistentDataType.LONG, now + t.shelfLifeMs());
        pdc.set(kCatcher, PersistentDataType.STRING, catcher);
        if (perfect) pdc.set(kPerfect, PersistentDataType.BYTE, (byte) 1);
        apply(item, meta, pdc, t, now);
        return item;
    }

    public ItemStack fridgeItem(int amount) {
        ItemStack item = new ItemStack(Material.BARREL, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(PescaPlugin.color("&r&b❄ Heladera de pesca"));
        meta.setLore(List.of(
                PescaPlugin.color("&r&7Guarda aquí tus peces para"),
                PescaPlugin.color("&r&7que no se dañen mientras esté cerrada.")));
        meta.getPersistentDataContainer().set(kFridge, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isFridgeItem(ItemStack it) {
        if (it == null || it.getType() != Material.BARREL || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(kFridge, PersistentDataType.BYTE);
    }

    // ---------------------------------------------------------------- leer

    /** @return null si el item no es un pez de este plugin. */
    public Info read(ItemStack item, long now) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(kId, PersistentDataType.STRING);
        if (id == null) return null;
        FishType t = registry.get(id);
        Double w = pdc.get(kWeight, PersistentDataType.DOUBLE);
        Long exp = pdc.get(kExpires, PersistentDataType.LONG);
        if (t == null || w == null || exp == null) return null;
        Long fr = pdc.get(kFrozen, PersistentDataType.LONG);
        return new Info(t, w, stateOf(t, exp, fr == null ? 0L : fr, now));
    }

    public double value(Info info, int level) {
        double v = info.type().price() * (info.weight() / info.type().avgWeight())
                * (1.0 + plugin.sellBonusPerLevel() * (level - 1));
        return Math.round(v * 100.0) / 100.0;
    }

    private State stateOf(FishType t, long expires, long frozenAt, long now) {
        long ref = frozenAt > 0 ? frozenAt : now;
        long remaining = expires - ref;
        if (remaining <= 0) return State.SPOILED;
        if (remaining <= t.shelfLifeMs() * warnFraction) return State.WARNING;
        return State.FRESH;
    }

    // ---------------------------------------------------------------- estados

    /** Actualiza nombre/lore solo si el estado cambio. @return true si se modifico el item. */
    public boolean refresh(ItemStack item, long now) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String id = pdc.get(kId, PersistentDataType.STRING);
        if (id == null) return false;
        FishType t = registry.get(id);
        Long exp = pdc.get(kExpires, PersistentDataType.LONG);
        if (t == null || exp == null) return false;
        Long fr = pdc.get(kFrozen, PersistentDataType.LONG);
        int sig = sig(stateOf(t, exp, fr == null ? 0L : fr, now), fr != null);
        Integer stored = pdc.get(kSig, PersistentDataType.INTEGER);
        if (stored != null && stored == sig) return false;
        apply(item, meta, pdc, t, now);
        return true;
    }

    /** Congela un pez (al cerrar la heladera). */
    public boolean freeze(ItemStack item, long now) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String id = pdc.get(kId, PersistentDataType.STRING);
        if (id == null || pdc.has(kFrozen, PersistentDataType.LONG)) return false;
        FishType t = registry.get(id);
        Long exp = pdc.get(kExpires, PersistentDataType.LONG);
        if (t == null || exp == null || exp - now <= 0) return false; // ya podrido: no se salva
        pdc.set(kFrozen, PersistentDataType.LONG, now);
        apply(item, meta, pdc, t, now);
        return true;
    }

    /** Descongela un pez (al abrir/romper la heladera). */
    public boolean thaw(ItemStack item, long now) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String id = pdc.get(kId, PersistentDataType.STRING);
        Long fr = pdc.get(kFrozen, PersistentDataType.LONG);
        if (id == null || fr == null) return false;
        FishType t = registry.get(id);
        Long exp = pdc.get(kExpires, PersistentDataType.LONG);
        if (t == null || exp == null) return false;
        pdc.set(kExpires, PersistentDataType.LONG, exp + Math.max(0L, now - fr));
        pdc.remove(kFrozen);
        apply(item, meta, pdc, t, now);
        return true;
    }

    // ---------------------------------------------------------------- render

    private int sig(State s, boolean frozen) {
        return s.ordinal() | (frozen ? 4 : 0);
    }

    private void apply(ItemStack item, ItemMeta meta, PersistentDataContainer pdc, FishType t, long now) {
        Double w = pdc.get(kWeight, PersistentDataType.DOUBLE);
        Long exp = pdc.get(kExpires, PersistentDataType.LONG);
        if (w == null || exp == null) return;
        Long fr = pdc.get(kFrozen, PersistentDataType.LONG);
        boolean frozen = fr != null;
        State st = stateOf(t, exp, frozen ? fr : 0L, now);
        pdc.set(kSig, PersistentDataType.INTEGER, sig(st, frozen));
        String catcher = pdc.get(kCatcher, PersistentDataType.STRING);

        String name = st == State.SPOILED ? "&r&2Podrido &8" + t.name() : "&r" + t.color() + t.name();
        meta.setDisplayName(PescaPlugin.color(name));

        List<String> lore = new ArrayList<>(8);
        lore.add(l("&7Rareza: " + t.color() + t.rarity()));
        lore.add(l("&7Peso: &f" + String.format(Locale.US, "%.2f", w) + " kg"));
        lore.add(l("&7Valor base: &a" + String.format(Locale.US, "%.2f", t.price() * w / t.avgWeight())));
        lore.add(l(""));
        switch (st) {
            case FRESH -> lore.add(l("&aFresco"));
            case WARNING -> {
                lore.add(l("&e¡Se está dañando!"));
                lore.add(l("&7Guárdalo en una heladera."));
            }
            case SPOILED -> {
                lore.add(l("&cPodrido"));
                lore.add(l("&7Ya no se puede vender."));
            }
        }
        if (pdc.has(kPerfect, PersistentDataType.BYTE)) lore.add(l("&e★ Captura perfecta"));
        if (frozen && st != State.SPOILED) lore.add(l("&b❄ Conservado en heladera"));
        if (catcher != null) lore.add(l("&8Pescado por " + catcher));
        meta.setLore(lore);
        item.setItemMeta(meta);
        if (st == State.SPOILED && item.getType() != Material.ROTTEN_FLESH) item.setType(Material.ROTTEN_FLESH);
    }

    private String l(String s) {
        return PescaPlugin.color("&r" + s);
    }
}
