package me.pesca.fishing;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Cañas y carnadas (items con datos en PDC). */
public final class RodItems {
    public record RodDef(String id, String name, String color, int level, double price,
                         double speed, int luck, int control) {}

    public record BaitDef(String id, String name, Material material, double price, int pack,
                          double speed, int luck, int control) {}

    private final NamespacedKey kRod, kBait;
    private final Map<String, RodDef> rods = new LinkedHashMap<>();
    private final Map<String, BaitDef> baits = new LinkedHashMap<>();

    public RodItems(PescaPlugin plugin) {
        kRod = new NamespacedKey(plugin, "rod_id");
        kBait = new NamespacedKey(plugin, "bait_id");
    }

    public void load(FileConfiguration cfg) {
        rods.clear();
        baits.clear();
        ConfigurationSection rs = cfg.getConfigurationSection("canas");
        if (rs != null) {
            for (String id : rs.getKeys(false)) {
                ConfigurationSection s = rs.getConfigurationSection(id);
                if (s == null) continue;
                String key = id.toLowerCase(Locale.ROOT);
                rods.put(key, new RodDef(key, s.getString("nombre", id), s.getString("color", "&f"),
                        Math.max(1, s.getInt("nivel", 1)), s.getDouble("precio", 0),
                        s.getDouble("velocidad", 0), s.getInt("suerte", 0), s.getInt("control", 0)));
            }
        }
        ConfigurationSection bs = cfg.getConfigurationSection("carnadas");
        if (bs != null) {
            for (String id : bs.getKeys(false)) {
                ConfigurationSection s = bs.getConfigurationSection(id);
                if (s == null) continue;
                Material m = Material.matchMaterial(s.getString("material", "BEETROOT_SEEDS"));
                if (m == null || !m.isItem()) continue;
                String key = id.toLowerCase(Locale.ROOT);
                baits.put(key, new BaitDef(key, s.getString("nombre", id), m, s.getDouble("precio", 0),
                        Math.max(1, s.getInt("paquete", 16)), s.getDouble("velocidad", 0),
                        s.getInt("suerte", 0), s.getInt("control", 0)));
            }
        }
    }

    public ItemStack rodItem(RodDef d) {
        ItemStack it = new ItemStack(Material.FISHING_ROD);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(PescaPlugin.color("&r" + d.color() + d.name()));
        List<String> lore = new ArrayList<>();
        lore.add(PescaPlugin.color("&r&7Nivel requerido: &f" + d.level()));
        lore.add(PescaPlugin.color("&r"));
        lore.add(PescaPlugin.color("&r&b⚡ Velocidad: &f+" + Math.round(d.speed() * 100) + "%"));
        lore.add(PescaPlugin.color("&r&a☘ Suerte: &f+" + d.luck()));
        lore.add(PescaPlugin.color("&r&e◎ Control: &f+" + d.control()));
        m.setLore(lore);
        m.setUnbreakable(true);
        m.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        m.getPersistentDataContainer().set(kRod, PersistentDataType.STRING, d.id());
        it.setItemMeta(m);
        return it;
    }

    public ItemStack baitItem(BaitDef d, int amount) {
        ItemStack it = new ItemStack(d.material(), amount);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(PescaPlugin.color("&r&e" + d.name() + " &7(carnada)"));
        List<String> lore = new ArrayList<>();
        lore.add(PescaPlugin.color("&r&7Ponla en tu &fmano secundaria&7."));
        lore.add(PescaPlugin.color("&r&7Se gasta una por lanzamiento."));
        lore.add(PescaPlugin.color("&r"));
        lore.add(PescaPlugin.color("&r&b⚡ Velocidad: &f+" + Math.round(d.speed() * 100) + "%"));
        lore.add(PescaPlugin.color("&r&a☘ Suerte: &f+" + d.luck()));
        lore.add(PescaPlugin.color("&r&e◎ Control: &f+" + d.control()));
        m.setLore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        m.getPersistentDataContainer().set(kBait, PersistentDataType.STRING, d.id());
        it.setItemMeta(m);
        return it;
    }

    public RodDef rodOf(ItemStack it) {
        if (it == null || it.getType() != Material.FISHING_ROD || !it.hasItemMeta()) return null;
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(kRod, PersistentDataType.STRING);
        return id == null ? null : rods.get(id);
    }

    public BaitDef baitOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(kBait, PersistentDataType.STRING);
        return id == null ? null : baits.get(id);
    }

    public Map<String, RodDef> rods() {
        return rods;
    }

    public Map<String, BaitDef> baits() {
        return baits;
    }
}
