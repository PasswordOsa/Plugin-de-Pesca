package me.pesca.fishing;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.*;

/** Misiones diarias: las mismas 3 para todos, cambian cada dia. */
public final class QuestManager {
    public record Def(String id, String desc, String type, String param, int amount, double money, int xp) {}

    private final PescaPlugin plugin;
    private final List<Def> pool = new ArrayList<>();
    private int perDay = 3;
    private long cachedDay = Long.MIN_VALUE;
    private List<Def> today = List.of();

    public QuestManager(PescaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration cfg) {
        pool.clear();
        cachedDay = Long.MIN_VALUE;
        perDay = Math.max(1, Math.min(5, cfg.getInt("misiones.por-dia", 3)));
        ConfigurationSection ps = cfg.getConfigurationSection("misiones.pool");
        if (ps == null) return;
        for (String id : ps.getKeys(false)) {
            ConfigurationSection s = ps.getConfigurationSection(id);
            if (s == null) continue;
            pool.add(new Def(id, s.getString("desc", id), s.getString("tipo", "cualquiera").toLowerCase(Locale.ROOT),
                    s.getString("parametro", ""), Math.max(1, s.getInt("cantidad", 1)),
                    s.getDouble("dinero", 0), s.getInt("xp", 0)));
        }
    }

    public long dayNow() {
        return LocalDate.now().toEpochDay();
    }

    public List<Def> today() {
        long day = dayNow();
        if (day != cachedDay) {
            List<Def> l = new ArrayList<>(pool);
            Collections.shuffle(l, new Random(day * 31L + 7L));
            today = List.copyOf(l.subList(0, Math.min(perDay, l.size())));
            cachedDay = day;
        }
        return today;
    }

    /** Reinicia el progreso si cambio el dia. */
    public void ensure(PlayerData d) {
        long day = dayNow();
        if (d.questDay != day) {
            d.questDay = day;
            Arrays.fill(d.questProg, 0);
            d.questClaimed = 0;
            d.dirty = true;
        }
    }

    private boolean matches(Def q, FishType t, boolean perfect) {
        return switch (q.type()) {
            case "cualquiera" -> true;
            case "rareza" -> t.rarity().equalsIgnoreCase(q.param());
            case "especie" -> t.id().equalsIgnoreCase(q.param());
            case "perfecta" -> perfect;
            default -> false;
        };
    }

    public void onCatch(Player p, PlayerData d, FishType t, boolean perfect) {
        ensure(d);
        List<Def> list = today();
        for (int i = 0; i < list.size(); i++) {
            Def q = list.get(i);
            if ((d.questClaimed & (1 << i)) != 0 || d.questProg[i] >= q.amount()) continue;
            if (!matches(q, t, perfect)) continue;
            d.questProg[i]++;
            d.dirty = true;
            if (d.questProg[i] >= q.amount()) {
                p.sendMessage(plugin.msg("mision-completada"));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        }
    }

    public boolean isDone(PlayerData d, int i) {
        List<Def> l = today();
        return i < l.size() && d.questProg[i] >= l.get(i).amount();
    }

    public boolean isClaimed(PlayerData d, int i) {
        return (d.questClaimed & (1 << i)) != 0;
    }

    public boolean claim(Player p, PlayerData d, int i) {
        ensure(d);
        List<Def> l = today();
        if (i >= l.size() || isClaimed(d, i) || !isDone(d, i)) return false;
        Def q = l.get(i);
        d.questClaimed |= (1 << i);
        d.dirty = true;
        plugin.economy().deposit(p, d, q.money());
        d.totalEarned += q.money();
        plugin.addXp(p, d, q.xp());
        p.sendMessage(plugin.msg("mision-cobrada", "dinero", plugin.economy().format(q.money()), "xp", q.xp()));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        return true;
    }
}
