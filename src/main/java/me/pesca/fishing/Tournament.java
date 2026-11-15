package me.pesca.fishing;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Torneo: gana quien acumule mas kilos pescados durante el tiempo. Barra de jefe con el lider. */
public final class Tournament {
    private final PescaPlugin plugin;
    private BossBar bar;
    private BukkitTask ticker, autoTask;
    private long endAt, totalMs;
    private final Map<UUID, Double> scores = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();

    public Tournament(PescaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean active() {
        return bar != null;
    }

    public void schedule() {
        if (autoTask != null) autoTask.cancel();
        autoTask = null;
        long every = plugin.getConfig().getLong("torneo.automatico-cada-minutos", 0);
        if (every <= 0) return;
        int dur = plugin.getConfig().getInt("torneo.duracion-minutos", 10);
        long period = every * 1200L;
        autoTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> start(dur), period, period);
    }

    public boolean start(int minutes) {
        if (active()) return false;
        totalMs = Math.max(1, minutes) * 60_000L;
        endAt = System.currentTimeMillis() + totalMs;
        scores.clear();
        names.clear();
        bar = Bukkit.createBossBar(PescaPlugin.color("&b&lTORNEO DE PESCA"), BarColor.BLUE, BarStyle.SEGMENTED_10);
        for (Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);
        Bukkit.broadcastMessage(plugin.msg("torneo-inicio", "minutos", minutes));
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        return true;
    }

    public void join(Player p) {
        if (bar != null) bar.addPlayer(p);
    }

    public void onCatch(Player p, double weight) {
        if (!active()) return;
        scores.merge(p.getUniqueId(), weight, Double::sum);
        names.put(p.getUniqueId(), p.getName());
    }

    private void tick() {
        long left = endAt - System.currentTimeMillis();
        if (left <= 0) {
            stop(true);
            return;
        }
        bar.setProgress(Math.max(0.0, Math.min(1.0, (double) left / totalMs)));
        List<Map.Entry<UUID, Double>> top = top(1);
        String lead = top.isEmpty() ? "&7sin capturas"
                : "&e1º " + names.get(top.get(0).getKey()) + " &7" + fmt(top.get(0).getValue()) + " kg";
        bar.setTitle(PescaPlugin.color("&b&lTORNEO &7| &f" + time(left) + " &7| " + lead));
    }

    public void stop(boolean award) {
        if (!active()) return;
        if (ticker != null) ticker.cancel();
        ticker = null;
        List<Map.Entry<UUID, Double>> top = top(3);
        if (award) {
            Bukkit.broadcastMessage(plugin.msg("torneo-fin"));
            List<Double> prizes = plugin.getConfig().getDoubleList("torneo.premios");
            String[] medal = {"&6&l1º", "&7&l2º", "&c&l3º"};
            for (int i = 0; i < top.size(); i++) {
                UUID id = top.get(i).getKey();
                double prize = i < prizes.size() ? prizes.get(i) : 0;
                Bukkit.broadcastMessage(PescaPlugin.color(medal[i] + " &f" + names.get(id) + " &8- &a"
                        + fmt(top.get(i).getValue()) + " kg" + (prize > 0 ? " &8| &e" + plugin.economy().format(prize) : "")));
                Player p = Bukkit.getPlayer(id);
                if (p != null && prize > 0) {
                    PlayerData d = plugin.data().get(id);
                    plugin.economy().deposit(p, d, prize);
                    d.totalEarned += prize;
                    d.dirty = true;
                }
            }
            if (top.isEmpty()) Bukkit.broadcastMessage(PescaPlugin.color("&7Nadie pescó nada. ¡Mejor suerte la próxima!"));
        }
        bar.removeAll();
        bar = null;
        scores.clear();
        names.clear();
    }

    public void shutdown() {
        stop(false);
        if (autoTask != null) autoTask.cancel();
    }

    public List<Map.Entry<UUID, Double>> top(int n) {
        List<Map.Entry<UUID, Double>> l = new ArrayList<>(scores.entrySet());
        l.sort(Map.Entry.<UUID, Double>comparingByValue().reversed());
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }

    public List<String> lines() {
        if (!active()) return List.of("&7No hay ningún torneo activo.");
        List<String> l = new ArrayList<>();
        l.add("&7Tiempo restante: &f" + time(Math.max(0, endAt - System.currentTimeMillis())));
        l.add("&7");
        int i = 1;
        for (Map.Entry<UUID, Double> e : top(5)) l.add("&e" + (i++) + "º &f" + names.get(e.getKey()) + " &8- &a" + fmt(e.getValue()) + " kg");
        if (i == 1) l.add("&8Aún no hay capturas");
        return l;
    }

    private String time(long ms) {
        long s = ms / 1000;
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private String fmt(double v) {
        return String.format(Locale.US, "%.2f", v);
    }
}
