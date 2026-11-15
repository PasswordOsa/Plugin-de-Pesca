package me.pesca.fishing;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Flujo de pesca estilo simulador:
 *  lanzar (caña + carnada + condiciones) -> picar (se elige el pez, aparece la barra de tension)
 *  -> recoger en el momento justo (fallo / buena / perfecta).
 * Solo hay una tarea corta mientras un pez esta picando; el resto es puramente por eventos.
 */
public final class FishingListener implements Listener {
    private static final class Session {
        FishType fish;
        boolean vanilla;
        Zone zone;
        boolean night, rain, moon;
        double speed;
        int luck, control;
        int center, half;
        double cellsPerSec;
        long biteAt;
        BukkitTask task;
    }

    private enum Quality { MISS, GOOD, PERFECT }

    private final PescaPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private int minWait = 100, maxWait = 600, width = 27, baseZone = 9, broadcastRank = 3;
    private double rainFactor = 0.8, baseSpeed = 10.0, speedPerRank = 2.5;

    public FishingListener(PescaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        var c = plugin.getConfig();
        minWait = Math.max(20, c.getInt("espera.minimo", 100));
        maxWait = Math.max(minWait + 20, c.getInt("espera.maximo", 600));
        rainFactor = c.getDouble("espera.lluvia-factor", 0.8);
        width = Math.max(15, c.getInt("minijuego.ancho", 27));
        baseZone = c.getInt("minijuego.zona-base", 9);
        baseSpeed = c.getDouble("minijuego.velocidad-base", 10.0);
        speedPerRank = c.getDouble("minijuego.velocidad-por-rareza", 2.5);
        broadcastRank = c.getInt("anuncios.rank-minimo", 3);
    }

    public void cleanup(UUID id) {
        Session s = sessions.remove(id);
        if (s != null && s.task != null) s.task.cancel();
    }

    public void shutdown() {
        for (Session s : sessions.values()) if (s.task != null) s.task.cancel();
        sessions.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPermission("pesca.use")) return;
        switch (e.getState()) {
            case FISHING -> onCast(e, p);
            case BITE -> onBite(e, p);
            case CAUGHT_FISH -> onCatch(e, p);
            case FAILED_ATTEMPT -> {
                Session s = sessions.get(p.getUniqueId());
                if (s != null && s.fish != null) p.sendMessage(plugin.msg("fallo-pez"));
                cleanup(p.getUniqueId());
            }
            case REEL_IN, IN_GROUND, CAUGHT_ENTITY -> cleanup(p.getUniqueId());
            default -> { }
        }
    }

    // ------------------------------------------------------------ lanzar

    private void onCast(PlayerFishEvent e, Player p) {
        cleanup(p.getUniqueId());
        PlayerData d = plugin.data().get(p.getUniqueId());
        PlayerInventory inv = p.getInventory();
        EquipmentSlot hand = e.getHand() == null ? EquipmentSlot.HAND : e.getHand();
        Session s = new Session();

        RodItems rods = plugin.rods();
        RodItems.RodDef rd = rods.rodOf(inv.getItem(hand));
        if (rd != null) {
            if (d.level >= rd.level()) {
                s.speed += rd.speed();
                s.luck += rd.luck();
                s.control += rd.control();
            } else {
                p.sendMessage(plugin.msg("cana-nivel", "nivel", rd.level()));
            }
        }

        EquipmentSlot baitSlot = hand == EquipmentSlot.HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        ItemStack baitItem = inv.getItem(baitSlot);
        RodItems.BaitDef bd = rods.baitOf(baitItem);
        if (bd != null) {
            s.speed += bd.speed();
            s.luck += bd.luck();
            s.control += bd.control();
            baitItem.setAmount(baitItem.getAmount() - 1);
            inv.setItem(baitSlot, baitItem.getAmount() <= 0 ? null : baitItem);
            sendBar(p, "&7Carnada: &e" + bd.name());
        }

        FishHook hook = e.getHook();
        Location hl = hook.getLocation();
        World w = hl.getWorld();
        s.zone = Zone.at(hl);
        long time = w.getTime();
        s.night = time >= 13000 && time < 23000;
        s.rain = w.hasStorm();
        s.moon = (w.getFullTime() / 24000L) % 8L == 0L;

        double f = 1.0 / (1.0 + s.speed);
        if (s.rain) f *= rainFactor;
        int min = Math.max(20, (int) (minWait * f));
        int max = Math.max(min + 20, (int) (maxWait * f));
        try {
            hook.setMinWaitTime(20);
            hook.setMaxWaitTime(max);
            hook.setMinWaitTime(min);
        } catch (IllegalArgumentException ignored) {
            // valores incoherentes: se deja el comportamiento vanilla
        }
        sessions.put(p.getUniqueId(), s);
    }

    // ------------------------------------------------------------ picar

    private void onBite(PlayerFishEvent e, Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (rnd.nextDouble() < plugin.vanillaLootChance()) {
            s.vanilla = true;
            return;
        }
        PlayerData d = plugin.data().get(p.getUniqueId());
        FishType t = plugin.registry().pick(d.level, s.zone, s.night, s.rain, s.moon, s.luck, rnd);
        if (t == null) {
            s.vanilla = true;
            return;
        }
        s.fish = t;
        int zoneW = Math.max(3, Math.min(13, baseZone - t.rank() + s.control));
        s.half = zoneW / 2;
        s.center = s.half + rnd.nextInt(Math.max(1, width - 2 * s.half));
        s.cellsPerSec = baseSpeed + speedPerRank * t.rank();
        s.biteAt = System.currentTimeMillis();

        FishHook hook = e.getHook();
        hook.getWorld().spawnParticle(Particle.FISHING, hook.getLocation(), 10, 0.25, 0.05, 0.25, 0.02);
        p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1f, 1.3f);
        p.sendTitle("", PescaPlugin.color("&b&l¡PICÓ! &7" + feel(t.rank())), 0, 20, 5);

        final UUID id = p.getUniqueId();
        s.task = new BukkitRunnable() {
            @Override
            public void run() {
                long el = System.currentTimeMillis() - s.biteAt;
                if (!p.isOnline() || sessions.get(id) != s || el > 5000) {
                    cancel();
                    return;
                }
                drawBar(p, s, cursor(s, el));
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private String feel(int rank) {
        return switch (rank) {
            case 0 -> "Se siente ligero";
            case 1 -> "Se siente moderado";
            case 2 -> "Se siente pesado";
            case 3 -> "¡Se siente muy pesado!";
            default -> "¡¡Algo ENORME tira del sedal!!";
        };
    }

    /** Posicion del cursor: va y viene por la barra a velocidad constante. */
    private int cursor(Session s, long elapsedMs) {
        double cells = elapsedMs / 1000.0 * s.cellsPerSec;
        double period = 2.0 * (width - 1);
        double m = cells % period;
        double pos = m <= (width - 1) ? m : period - m;
        return (int) Math.round(pos);
    }

    private void drawBar(Player p, Session s, int cur) {
        int perfectHalf = s.half / 2;
        StringBuilder sb = new StringBuilder("§b≈ §8[");
        for (int i = 0; i < width; i++) {
            int dist = Math.abs(i - s.center);
            if (i == cur) sb.append("§f§l█");
            else if (dist <= perfectHalf) sb.append("§e|");
            else if (dist <= s.half) sb.append("§a|");
            else sb.append("§8|");
        }
        sb.append("§8] §b≈");
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(sb.toString()));
    }

    private void sendBar(Player p, String text) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(PescaPlugin.color(text)));
    }

    // ------------------------------------------------------------ recoger

    private void onCatch(PlayerFishEvent e, Player p) {
        Session s = sessions.remove(p.getUniqueId());
        if (s != null && s.task != null) s.task.cancel();
        if (!(e.getCaught() instanceof Item drop)) return;
        if (s == null || s.vanilla || s.fish == null) return; // loot vanilla

        FishType t = s.fish;
        int dist = Math.abs(cursor(s, System.currentTimeMillis() - s.biteAt) - s.center);
        Quality q = dist <= s.half / 2 ? Quality.PERFECT : dist <= s.half ? Quality.GOOD : Quality.MISS;

        if (q == Quality.MISS) {
            drop.remove();
            p.sendMessage(plugin.msg("se-escapo"));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }

        boolean perfect = q == Quality.PERFECT;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double r = rnd.nextDouble();
        if (perfect) r = Math.max(r, rnd.nextDouble()); // la captura perfecta tiende a pesar mas
        double w = Math.round((t.minWeight() + r * (t.maxWeight() - t.minWeight())) * 100.0) / 100.0;
        drop.setItemStack(plugin.items().create(t, w, p.getName(), perfect));

        PlayerData d = plugin.data().get(p.getUniqueId());
        int count = d.caught.merge(t.id(), 1, Integer::sum);
        Double rec = d.record.get(t.id());
        if (rec == null || w > rec) d.record.put(t.id(), w);
        d.totalCaught++;
        if (perfect) d.perfect++;
        d.dirty = true;

        long xp = Math.round(t.xp() * (perfect ? 1.5 : 1.0));
        p.sendMessage(plugin.msg("atrapado", "color", t.color(), "pez", t.name(),
                "peso", String.format(Locale.US, "%.2f", w),
                "calidad", perfect ? plugin.raw("calidad-perfecta") : "", "xp", xp));
        if (count == 1) p.sendMessage(plugin.msg("nuevo-pez", "pez", t.name()));

        effects(p, t, perfect, w);
        plugin.addXp(p, d, xp);
        plugin.quests().onCatch(p, d, t, perfect);
        plugin.tournament().onCatch(p, w);

        ServerRecords rs = plugin.records();
        ServerRecords.Outcome o = rs.check(t.id(), w, p.getName());
        if (o == ServerRecords.Outcome.BEATEN) {
            ServerRecords.Rec prev = rs.previous();
            Bukkit.broadcastMessage(plugin.msg("record-servidor", "jugador", p.getName(), "color", t.color(),
                    "pez", t.name(), "peso", String.format(Locale.US, "%.2f", w),
                    "antes", String.format(Locale.US, "%.2f", prev.weight()), "antes_jugador", prev.holder()));
        }
    }

    private void effects(Player p, FishType t, boolean perfect, double w) {
        Location loc = p.getLocation();
        if (perfect) p.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.7f);
        else p.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.1f);
        if (t.rank() >= broadcastRank) {
            p.getWorld().spawnParticle(Particle.FIREWORK, loc.clone().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.05);
            p.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            p.sendTitle(PescaPlugin.color(t.color() + "&l" + t.rarity().toUpperCase(Locale.ROOT)),
                    PescaPlugin.color("&f" + t.name()), 5, 40, 10);
            Bukkit.broadcastMessage(plugin.msg("anuncio-captura", "jugador", p.getName(), "color", t.color(),
                    "pez", t.name(), "peso", String.format(Locale.US, "%.2f", w)));
        }
    }
}
