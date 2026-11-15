package me.pesca.fishing;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PescaCommand implements TabExecutor {
    private final PescaPlugin plugin;

    public PescaCommand(PescaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sub = a.length == 0 ? "info" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info", "nivel" -> info(s);
            case "coleccion", "colección" -> collection(s);
            case "vender" -> sell(s, a.length > 1 && a[1].equalsIgnoreCase("mano"));
            case "limpiar" -> clean(s);
            case "reload" -> {
                if (!s.hasPermission("pesca.admin")) {
                    plugin.send(s, "sin-permiso");
                } else {
                    plugin.reloadAll();
                    plugin.send(s, "recargado");
                }
            }
            case "heladera" -> fridge(s, a);
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(PescaPlugin.color("&b/pesca info &7- tu nivel y estadísticas"));
        s.sendMessage(PescaPlugin.color("&b/pesca coleccion &7- tus peces descubiertos"));
        s.sendMessage(PescaPlugin.color("&b/pesca vender [mano] &7- vende tus peces frescos"));
        s.sendMessage(PescaPlugin.color("&b/pesca limpiar &7- tira los peces podridos"));
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p && p.hasPermission("pesca.use")) return p;
        plugin.send(s, s instanceof Player ? "sin-permiso" : "solo-jugadores");
        return null;
    }

    private void info(CommandSender s) {
        Player p = player(s);
        if (p == null) return;
        PlayerData d = plugin.data().get(p.getUniqueId());
        long need = plugin.xpToNext(d.level);
        EconomyBridge eco = plugin.economy();
        p.sendMessage(PescaPlugin.color("&b&lPesca"));
        p.sendMessage(PescaPlugin.color("&7Nivel: &f" + d.level + "&7/" + plugin.maxLevel()));
        p.sendMessage(PescaPlugin.color(need == 0 ? "&7XP: &6NIVEL MÁXIMO"
                : "&7XP: " + bar(d.xp, need) + " &f" + d.xp + "&7/&f" + need));
        p.sendMessage(PescaPlugin.color("&7Peces capturados: &f" + d.totalCaught));
        p.sendMessage(PescaPlugin.color("&7Especies: &f" + d.caught.size() + "&7/&f" + plugin.registry().all().size()));
        p.sendMessage(PescaPlugin.color("&7Dinero ganado: &a" + eco.format(d.totalEarned)));
        if (eco.internal()) p.sendMessage(PescaPlugin.color("&7Saldo interno: &a" + eco.format(d.balance)));
    }

    private String bar(long xp, long need) {
        int filled = (int) Math.min(20, (20L * xp) / Math.max(1, need));
        return "&a" + "|".repeat(filled) + "&8" + "|".repeat(20 - filled);
    }

    private void collection(CommandSender s) {
        Player p = player(s);
        if (p != null) CollectionGui.open(plugin, p, plugin.data().get(p.getUniqueId()));
    }

    private void sell(CommandSender s, boolean handOnly) {
        Player p = player(s);
        if (p != null) plugin.sales().sell(p, handOnly);
    }

    private void clean(CommandSender s) {
        Player p = player(s);
        if (p != null) plugin.sales().clean(p);
    }

    private void fridge(CommandSender s, String[] a) {
        if (!s.hasPermission("pesca.admin")) {
            plugin.send(s, "sin-permiso");
            return;
        }
        Player target = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player pl ? pl : null);
        if (target == null) {
            plugin.send(s, "jugador-no-encontrado");
            return;
        }
        target.getInventory().addItem(plugin.items().fridgeItem(1)).values()
                .forEach(it -> target.getWorld().dropItemNaturally(target.getLocation(), it));
        plugin.send(s, "heladera-dada", "jugador", target.getName());
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) {
            List<String> out = new ArrayList<>(List.of("info", "coleccion", "vender", "limpiar"));
            if (s.hasPermission("pesca.admin")) {
                out.add("reload");
                out.add("heladera");
            }
            String pre = a[0].toLowerCase(Locale.ROOT);
            out.removeIf(x -> !x.startsWith(pre));
            return out;
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("vender")) return List.of("mano");
        if (a.length == 2 && a[0].equalsIgnoreCase("heladera")) return null; // jugadores online
        return List.of();
    }
}
