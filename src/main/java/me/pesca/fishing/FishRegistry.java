package me.pesca.fishing;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/** Carga los peces y precalcula una tabla acumulada por nivel (seleccion O(log n), sin asignaciones). */
public final class FishRegistry {
    private final Map<String, FishType> byId = new LinkedHashMap<>();
    private FishType[][] tableFish = new FishType[0][];
    private double[][] tableCum = new double[0][];
    private int maxLevel = 1;

    public void load(FileConfiguration cfg, Logger log) {
        byId.clear();
        maxLevel = Math.max(1, cfg.getInt("nivel.maximo", 50));

        Map<String, String> colors = new HashMap<>();
        Map<String, Integer> rank = new HashMap<>();
        ConfigurationSection rs = cfg.getConfigurationSection("rarezas");
        if (rs != null) {
            int i = 0;
            for (String k : rs.getKeys(false)) {
                colors.put(k, rs.getString(k, "&f"));
                rank.put(k, i++);
            }
        }

        ConfigurationSection fs = cfg.getConfigurationSection("peces");
        if (fs != null) {
            for (String id : fs.getKeys(false)) {
                ConfigurationSection s = fs.getConfigurationSection(id);
                if (s == null) continue;
                Material mat = Material.matchMaterial(s.getString("material", "COD"));
                if (mat == null || !mat.isItem()) {
                    log.warning("Pez '" + id + "': material invalido, se omite.");
                    continue;
                }
                String rarity = s.getString("rareza", "Común");
                double minW = s.getDouble("peso-min", 0.5);
                double maxW = Math.max(minW, s.getDouble("peso-max", 1.0));
                String key = id.toLowerCase(Locale.ROOT);
                byId.put(key, new FishType(
                        key,
                        s.getString("nombre", id),
                        mat,
                        rarity,
                        colors.getOrDefault(rarity, "&f"),
                        Math.max(1, s.getInt("nivel-minimo", 1)),
                        Math.max(0.0001, s.getDouble("probabilidad", 1)),
                        s.getDouble("precio", 1),
                        s.getInt("xp", 1),
                        minW, maxW,
                        (long) (s.getDouble("vida-minutos", 30) * 60_000L)));
            }
        }
        if (byId.isEmpty()) log.warning("No hay peces validos en config.yml");

        double bonus = cfg.getDouble("nivel.bonus-rareza-por-nivel", 0.04);
        tableFish = new FishType[maxLevel + 1][];
        tableCum = new double[maxLevel + 1][];
        for (int lvl = 1; lvl <= maxLevel; lvl++) {
            List<FishType> el = new ArrayList<>();
            for (FishType t : byId.values()) if (t.minLevel() <= lvl) el.add(t);
            FishType[] arr = el.toArray(new FishType[0]);
            double[] cum = new double[arr.length];
            double sum = 0;
            for (int i = 0; i < arr.length; i++) {
                int idx = rank.getOrDefault(arr[i].rarity(), 0);
                sum += arr[i].chance() * (1.0 + bonus * (lvl - 1) * idx);
                cum[i] = sum;
            }
            tableFish[lvl] = arr;
            tableCum[lvl] = cum;
        }
    }

    public FishType pick(int level, ThreadLocalRandom rnd) {
        int lv = Math.min(Math.max(level, 1), maxLevel);
        double[] c = tableCum[lv];
        if (c.length == 0) return null;
        double x = rnd.nextDouble() * c[c.length - 1];
        int lo = 0, hi = c.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (c[mid] > x) hi = mid;
            else lo = mid + 1;
        }
        return tableFish[lv][lo];
    }

    public FishType get(String id) {
        return byId.get(id);
    }

    public Collection<FishType> all() {
        return byId.values();
    }
}
