package me.pesca.fishing;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.Map;

/** Record de peso del servidor por especie. */
public final class ServerRecords {
    public record Rec(double weight, String holder) {}

    public enum Outcome { NONE, FIRST, BEATEN }

    private final Map<String, Rec> map = new HashMap<>();
    private boolean dirty;
    private Rec previous;

    public void load(YamlConfiguration y) {
        ConfigurationSection s = y.getConfigurationSection("records");
        if (s == null) return;
        for (String k : s.getKeys(false)) map.put(k, new Rec(s.getDouble(k + ".peso"), s.getString(k + ".jugador", "?")));
    }

    public String toYaml() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Rec> e : map.entrySet()) {
            y.set("records." + e.getKey() + ".peso", e.getValue().weight());
            y.set("records." + e.getKey() + ".jugador", e.getValue().holder());
        }
        dirty = false;
        return y.saveToString();
    }

    public boolean dirty() {
        return dirty;
    }

    public Rec get(String id) {
        return map.get(id);
    }

    /** Record anterior de la ultima llamada a check() que devolvio BEATEN. */
    public Rec previous() {
        return previous;
    }

    public Outcome check(String id, double weight, String player) {
        Rec cur = map.get(id);
        if (cur == null) {
            map.put(id, new Rec(weight, player));
            dirty = true;
            return Outcome.FIRST;
        }
        if (weight > cur.weight()) {
            previous = cur;
            map.put(id, new Rec(weight, player));
            dirty = true;
            return Outcome.BEATEN;
        }
        return Outcome.NONE;
    }
}
