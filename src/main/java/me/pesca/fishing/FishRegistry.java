package me.pesca.fishing;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Tablas de probabilidad acumuladas, construidas de forma perezosa por contexto
 * (nivel, zona, dia/noche, lluvia, luna llena, suerte) y cacheadas.
 * Elegir un pez = 1 busqueda binaria, sin asignaciones.
 */
public final class FishRegistry {
    private record Table(FishType[] fish, double[] cum) {}

    private static final int ALL_ZONES = (1 << Zone.values().length) - 1;

    private final Map<String, FishType> byId = new LinkedHashMap<>();
    private final List<String> rarityNames = new ArrayList<>();
    private final Map<String, Integer> rankOf = new HashMap<>();
    private final ConcurrentHashMap<Long, Table> cache = new ConcurrentHashMap<>();
    private int maxLevel = 50;
    private double levelBonus = 0.04;

    public void load(FileConfiguration cfg, Logger log) {
        byId.clear();
        rarityNames.clear();
        rankOf.clear();
        cache.clear();
        maxLevel = Math.max(1, cfg.getInt("nivel.maximo", 50));
        levelBonus = cfg.getDouble("nivel.bonus-rareza-por-nivel", 0.04);

        Map<String, String> colors = new HashMap<>();
        ConfigurationSection rs = cfg.getConfigurationSection("rarezas");
        if (rs != null) {
            int i = 0;
            for (String k : rs.getKeys(false)) {
                colors.put(k, rs.getString(k, "&f"));
                rankOf.put(k, i++);
                rarityNames.add(k);
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
                String rarity = s.getString("rareza", rarityNames.isEmpty() ? "Común" : rarityNames.get(0));
                double minW = s.getDouble("peso-min", 0.5);
                double maxW = Math.max(minW, s.getDouble("peso-max", 1.0));

                int mask = 0;
                for (String z : s.getStringList("zonas")) {
                    try {
                        mask |= 1 << Zone.valueOf(z.trim().toUpperCase(Locale.ROOT)).ordinal();
                    } catch (IllegalArgumentException ex) {
                        log.warning("Pez '" + id + "': zona desconocida '" + z + "'");
                    }
                }
                if (mask == 0) mask = ALL_ZONES;

                String h = s.getString("horario", "CUALQUIERA").toUpperCase(Locale.ROOT);
                int time = h.equals("DIA") ? 1 : h.equals("NOCHE") ? 2 : 0;
                String c = s.getString("clima", "CUALQUIERA").toUpperCase(Locale.ROOT);
                int weather = c.equals("LLUVIA") ? 1 : c.equals("DESPEJADO") ? 2 : 0;

                String key = id.toLowerCase(Locale.ROOT);
                byId.put(key, new FishType(key, s.getString("nombre", id), s.getString("descripcion", ""),
                        mat, rarity, colors.getOrDefault(rarity, "&f"), rankOf.getOrDefault(rarity, 0),
                        Math.max(1, s.getInt("nivel-minimo", 1)),
                        Math.max(0.0001, s.getDouble("probabilidad", 1)),
                        s.getDouble("precio", 1), s.getInt("xp", 1), minW, maxW,
                        (long) (s.getDouble("vida-minutos", 30) * 60_000L),
                        mask, time, weather));
            }
        }
        if (byId.isEmpty()) log.warning("No hay peces validos en config.yml");
    }

    public FishType pick(int level, Zone zone, boolean night, boolean rain, boolean moon, int luck,
                         ThreadLocalRandom rnd) {
        final int lv = Math.min(Math.max(level, 1), maxLevel);
        final int lk = Math.min(15, Math.max(0, luck));
        long key = lv | ((long) zone.ordinal() << 8) | ((night ? 1L : 0L) << 11)
                | ((rain ? 1L : 0L) << 12) | ((moon ? 1L : 0L) << 13) | ((long) lk << 14);
        Table t = cache.computeIfAbsent(key, k -> build(lv, zone, night, rain, moon, lk));
        double[] c = t.cum();
        if (c.length == 0) return null;
        double x = rnd.nextDouble() * c[c.length - 1];
        int lo = 0, hi = c.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (c[mid] > x) hi = mid;
            else lo = mid + 1;
        }
        return t.fish()[lo];
    }

    private Table build(int lvl, Zone z, boolean night, boolean rain, boolean moon, int luck) {
        for (int pass = 0; pass < 2; pass++) { // pass 1 = ignora zona/horario/clima (nunca queda vacio)
            List<FishType> el = new ArrayList<>();
            for (FishType t : byId.values()) {
                if (t.minLevel() > lvl) continue;
                if (pass == 0) {
                    if (!t.inZone(z)) continue;
                    if (t.time() == 1 && night) continue;
                    if (t.time() == 2 && !night) continue;
                    if (t.weather() == 1 && !rain) continue;
                    if (t.weather() == 2 && rain) continue;
                }
                el.add(t);
            }
            if (el.isEmpty()) continue;
            FishType[] arr = el.toArray(new FishType[0]);
            double[] cum = new double[arr.length];
            double sum = 0;
            for (int i = 0; i < arr.length; i++) {
                FishType t = arr[i];
                double rare = levelBonus * (lvl - 1) + 0.15 * luck + (moon && night ? 0.25 : 0.0);
                double w = t.chance() * (1.0 + rare * t.rank());
                if (pass == 0) { // especialistas: mas probables en sus condiciones ideales
                    if (t.weather() == 1 && rain) w *= 1.5;
                    if (t.time() != 0) w *= 1.3;
                }
                sum += w;
                cum[i] = sum;
            }
            return new Table(arr, cum);
        }
        return new Table(new FishType[0], new double[0]);
    }

    public FishType get(String id) {
        return byId.get(id);
    }

    public Collection<FishType> all() {
        return byId.values();
    }

    public List<String> rarityNames() {
        return rarityNames;
    }
}
