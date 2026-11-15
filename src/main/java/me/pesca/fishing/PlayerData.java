package me.pesca.fishing;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerData {
    public final UUID uuid;
    public int level = 1;
    public long xp;
    public int totalCaught;
    public int perfect;
    public double totalEarned;
    public double balance; // solo si no hay Vault
    public long questDay;
    public final int[] questProg = new int[5];
    public int questClaimed; // bitmask
    public final Map<String, Integer> caught = new HashMap<>();
    public final Map<String, Double> record = new HashMap<>();
    public volatile boolean dirty;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public static PlayerData fromYaml(UUID uuid, YamlConfiguration y) {
        PlayerData d = new PlayerData(uuid);
        d.level = Math.max(1, y.getInt("nivel", 1));
        d.xp = y.getLong("xp", 0L);
        d.totalCaught = y.getInt("total-capturados", 0);
        d.perfect = y.getInt("perfectas", 0);
        d.totalEarned = y.getDouble("total-ganado", 0);
        d.balance = y.getDouble("saldo", 0);
        d.questDay = y.getLong("mision-dia", 0L);
        List<Integer> qp = y.getIntegerList("mision-progreso");
        for (int i = 0; i < Math.min(qp.size(), d.questProg.length); i++) d.questProg[i] = qp.get(i);
        d.questClaimed = y.getInt("mision-cobradas", 0);
        ConfigurationSection c = y.getConfigurationSection("capturados");
        if (c != null) for (String k : c.getKeys(false)) d.caught.put(k, c.getInt(k));
        ConfigurationSection r = y.getConfigurationSection("record");
        if (r != null) for (String k : r.getKeys(false)) d.record.put(k, r.getDouble(k));
        return d;
    }

    public String toYaml() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("nivel", level);
        y.set("xp", xp);
        y.set("total-capturados", totalCaught);
        y.set("perfectas", perfect);
        y.set("total-ganado", totalEarned);
        y.set("saldo", balance);
        y.set("mision-dia", questDay);
        List<Integer> qp = new ArrayList<>();
        for (int v : questProg) qp.add(v);
        y.set("mision-progreso", qp);
        y.set("mision-cobradas", questClaimed);
        for (Map.Entry<String, Integer> e : caught.entrySet()) y.set("capturados." + e.getKey(), e.getValue());
        for (Map.Entry<String, Double> e : record.entrySet()) y.set("record." + e.getKey(), e.getValue());
        return y.saveToString();
    }
}
