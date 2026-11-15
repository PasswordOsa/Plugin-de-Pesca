package me.pesca.fishing;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerData {
    public final UUID uuid;
    public int level = 1;
    public long xp;
    public int totalCaught;
    public double totalEarned;
    public double balance; // solo se usa si no hay Vault
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
        d.totalEarned = y.getDouble("total-ganado", 0);
        d.balance = y.getDouble("saldo", 0);
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
        y.set("total-ganado", totalEarned);
        y.set("saldo", balance);
        for (Map.Entry<String, Integer> e : caught.entrySet()) y.set("capturados." + e.getKey(), e.getValue());
        for (Map.Entry<String, Double> e : record.entrySet()) y.set("record." + e.getKey(), e.getValue());
        return y.saveToString();
    }
}
