package me.pesca.fishing;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mercado dinamico: vender muchos peces de una especie baja su precio (saturacion),
 * que se recupera con el tiempo. Ademas, una especie "en tendencia" rota cada cierto tiempo.
 */
public final class Market {
    public record Preview(int sellable, int spoiled, double estimate, Map<String, Integer> perSpecies) {}

    private final PescaPlugin plugin;
    private final Map<String, Double> demand = new HashMap<>();
    private String trendId = "";
    private long trendUntil;
    private double saturation = 0.012, recovery = 0.01, floor = 0.5, trendBoost = 1.3;
    private long trendMs = 3_600_000L;
    private boolean dirty;

    public Market(PescaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration cfg) {
        saturation = cfg.getDouble("mercado.saturacion-por-pez", 0.012);
        recovery = cfg.getDouble("mercado.recuperacion-por-minuto", 0.01);
        floor = Math.max(0.05, cfg.getDouble("mercado.minimo", 0.5));
        trendBoost = cfg.getDouble("mercado.tendencia-bonus", 1.3);
        trendMs = (long) (cfg.getDouble("mercado.tendencia-minutos", 60) * 60_000L);
    }

    public void load(YamlConfiguration y) {
        ConfigurationSection s = y.getConfigurationSection("demanda");
        if (s != null) for (String k : s.getKeys(false)) demand.put(k, s.getDouble(k, 1.0));
        trendId = y.getString("tendencia.id", "");
        trendUntil = y.getLong("tendencia.hasta", 0L);
    }

    public String toYaml() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Double> e : demand.entrySet()) y.set("demanda." + e.getKey(), e.getValue());
        y.set("tendencia.id", trendId);
        y.set("tendencia.hasta", trendUntil);
        dirty = false;
        return y.saveToString();
    }

    public boolean dirty() {
        return dirty;
    }

    public double demand(FishType t) {
        return demand.getOrDefault(t.id(), 1.0);
    }

    public boolean trending(FishType t) {
        return t.id().equals(trendId) && System.currentTimeMillis() < trendUntil;
    }

    public double multiplier(FishType t) {
        return demand(t) * (trending(t) ? trendBoost : 1.0);
    }

    public double trendBoost() {
        return trendBoost;
    }

    public String trendId() {
        return trendId;
    }

    /** Se ejecuta cada minuto: recupera la demanda y rota la tendencia. */
    public void tick() {
        boolean changed = false;
        for (Map.Entry<String, Double> e : demand.entrySet()) {
            if (e.getValue() < 1.0) {
                e.setValue(Math.min(1.0, e.getValue() + recovery));
                changed = true;
            }
        }
        long now = System.currentTimeMillis();
        if (now >= trendUntil && !plugin.registry().all().isEmpty()) {
            List<FishType> l = new ArrayList<>(plugin.registry().all());
            FishType t = l.get(ThreadLocalRandom.current().nextInt(l.size()));
            trendId = t.id();
            trendUntil = now + trendMs;
            changed = true;
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                p.sendMessage(plugin.msg("tendencia", "color", t.color(), "pez", t.name(),
                        "bono", Math.round((trendBoost - 1.0) * 100), "minutos", trendMs / 60_000L));
            }
        }
        if (changed) dirty = true;
    }

    // ------------------------------------------------------------ ventas

    public Preview preview(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        long now = System.currentTimeMillis();
        PlayerInventory inv = p.getInventory();
        double est = 0;
        int sellable = 0, spoiled = 0;
        Map<String, Integer> per = new HashMap<>();
        for (int i = 0; i <= 35; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || !it.hasItemMeta()) continue;
            FishItems.Info info = plugin.items().read(it, now);
            if (info == null) continue;
            if (info.state() == FishItems.State.SPOILED) {
                spoiled += it.getAmount();
                continue;
            }
            est += plugin.items().value(info, d.level) * multiplier(info.type()) * it.getAmount();
            sellable += it.getAmount();
            per.merge(info.type().id(), it.getAmount(), Integer::sum);
        }
        return new Preview(sellable, spoiled, Math.round(est * 100.0) / 100.0, per);
    }

    public void sell(Player p, boolean handOnly) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        FishItems items = plugin.items();
        PlayerInventory inv = p.getInventory();
        long now = System.currentTimeMillis();
        int start = handOnly ? inv.getHeldItemSlot() : 0;
        int end = handOnly ? inv.getHeldItemSlot() : 35;

        double total = 0;
        int sold = 0, spoiled = 0;
        for (int i = start; i <= end; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || !it.hasItemMeta()) continue;
            FishItems.Info info = items.read(it, now);
            if (info == null) continue;
            if (info.state() == FishItems.State.SPOILED) {
                spoiled += it.getAmount();
                continue;
            }
            FishType t = info.type();
            for (int n = 0; n < it.getAmount(); n++) {
                total += items.value(info, d.level) * multiplier(t); // precio con la demanda actual
                demand.put(t.id(), Math.max(floor, demand(t) - saturation)); // cada venta satura el mercado
                sold++;
            }
            inv.setItem(i, null);
        }

        if (sold == 0) {
            p.sendMessage(plugin.msg("nada-para-vender"));
        } else {
            total = Math.round(total * 100.0) / 100.0;
            plugin.economy().deposit(p, d, total);
            d.totalEarned += total;
            d.dirty = true;
            dirty = true;
            p.sendMessage(plugin.msg("vendido", "cantidad", sold, "dinero", plugin.economy().format(total)));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        }
        if (spoiled > 0) p.sendMessage(plugin.msg("podridos-omitidos", "cantidad", spoiled));
    }

    /** Tira los peces podridos del inventario. */
    public void clean(Player p) {
        PlayerInventory inv = p.getInventory();
        long now = System.currentTimeMillis();
        int removed = 0;
        for (int i = 0; i <= 35; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || !it.hasItemMeta()) continue;
            FishItems.Info info = plugin.items().read(it, now);
            if (info != null && info.state() == FishItems.State.SPOILED) {
                removed += it.getAmount();
                inv.setItem(i, null);
            }
        }
        p.sendMessage(plugin.msg("limpiado", "cantidad", removed));
    }
}
