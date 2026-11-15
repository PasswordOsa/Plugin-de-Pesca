package me.pesca.fishing;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
        String sub = a.length == 0 ? "menu" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> open(s, 0);
            case "coleccion", "colección" -> open(s, 1);
            case "mercado" -> open(s, 2);
            case "tienda" -> open(s, 3);
            case "misiones" -> open(s, 4);
            case "info", "nivel" -> info(s);
            case "vender" -> {
                Player p = player(s);
                if (p != null) plugin.market().sell(p, a.length > 1 && a[1].equalsIgnoreCase("mano"));
            }
            case "limpiar" -> {
                Player p = player(s);
                if (p != null) plugin.market().clean(p);
            }
            case "torneo" -> tournament(s, a);
            case "reload" -> {
                if (!admin(s)) break;
                plugin.reloadAll();
                plugin.send(s, "recargado");
            }
            case "heladera" -> fridge(s, a);
            case "dar" -> give(s, a);
            default -> help(s);
        }
        return true;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("pesca.admin")) return true;
        plugin.send(s, "sin-permiso");
        return false;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p && p.hasPermission("pesca.use")) return p;
        plugin.send(s, s instanceof Player ? "sin-permiso" : "solo-jugadores");
        return null;
    }

    private void open(CommandSender s, int which) {
        Player p = player(s);
        if (p == null) return;
        switch (which) {
            case 1 -> new CollectionMenu(plugin, p).open();
            case 2 -> new MarketMenu(plugin, p).open();
            case 3 -> new ShopMenu(plugin, p).open();
            case 4 -> new QuestMenu(plugin, p).open();
            default -> new HubMenu(plugin, p).open();
        }
    }

    private void help(CommandSender s) {
        s.sendMessage(PescaPlugin.color("&b&lPescaPlus"));
        s.sendMessage(PescaPlugin.color("&b/pesca &7- menú principal"));
        s.sendMessage(PescaPlugin.color("&b/pesca coleccion|mercado|tienda|misiones"));
        s.sendMessage(PescaPlugin.color("&b/pesca info &7- tus estadísticas en el chat"));
        s.sendMessage(PescaPlugin.color("&b/pesca vender [mano] &7- vende tus peces frescos"));
        s.sendMessage(PescaPlugin.color("&b/pesca limpiar &7- tira los peces podridos"));
        if (s.hasPermission("pesca.admin")) {
            s.sendMessage(PescaPlugin.color("&c/pesca torneo <iniciar [min]|parar> &7- torneos"));
            s.sendMessage(PescaPlugin.color("&c/pesca heladera [jugador] &7- da una heladera"));
            s.sendMessage(PescaPlugin.color("&c/pesca dar <jugador> <cana|carnada> <id> [cantidad]"));
            s.sendMessage(PescaPlugin.color("&c/pesca reload"));
        }
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
                : "&7XP: " + Ui.bar((double) d.xp / need, 20) + " &f" + d.xp + "&7/&f" + need));
        p.sendMessage(PescaPlugin.color("&7Peces: &f" + d.totalCaught + " &8| &7Perfectas: &e" + d.perfect));
        p.sendMessage(PescaPlugin.color("&7Especies: &f" + d.caught.size() + "&7/&f" + plugin.registry().all().size()));
        p.sendMessage(PescaPlugin.color("&7Dinero ganado: &a" + eco.format(d.totalEarned)));
        if (eco.internal()) p.sendMessage(PescaPlugin.color("&7Saldo interno: &a" + eco.format(d.balance)));
    }

    private void tournament(CommandSender s, String[] a) {
        Tournament t = plugin.tournament();
        String act = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        if (act.equals("iniciar")) {
            if (!admin(s)) return;
            int min = plugin.getConfig().getInt("torneo.duracion-minutos", 10);
            if (a.length > 2) {
                try {
                    min = Math.max(1, Integer.parseInt(a[2]));
                } catch (NumberFormatException ignored) {
                    // se usa la duracion por defecto
                }
            }
            if (!t.start(min)) plugin.send(s, "torneo-ya");
        } else if (act.equals("parar")) {
            if (!admin(s)) return;
            if (!t.active()) plugin.send(s, "torneo-no");
            else t.stop(true);
        } else {
            if (!t.active()) {
                plugin.send(s, "torneo-no");
                return;
            }
            for (String l : t.lines()) s.sendMessage(PescaPlugin.color(l));
        }
    }

    private void fridge(CommandSender s, String[] a) {
        if (!admin(s)) return;
        Player target = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player pl ? pl : null);
        if (target == null) {
            plugin.send(s, "jugador-no-encontrado");
            return;
        }
        giveTo(target, plugin.items().fridgeItem(1));
        plugin.send(s, "heladera-dada", "jugador", target.getName());
    }

    private void give(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 4) {
            plugin.send(s, "uso-dar");
            return;
        }
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target == null) {
            plugin.send(s, "jugador-no-encontrado");
            return;
        }
        String id = a[3].toLowerCase(Locale.ROOT);
        ItemStack item = null;
        String name = id;
        if (a[2].equalsIgnoreCase("cana") || a[2].equalsIgnoreCase("caña")) {
            RodItems.RodDef rd = plugin.rods().rods().get(id);
            if (rd != null) {
                item = plugin.rods().rodItem(rd);
                name = rd.name();
            }
        } else if (a[2].equalsIgnoreCase("carnada")) {
            RodItems.BaitDef bd = plugin.rods().baits().get(id);
            if (bd != null) {
                int n = 16;
                if (a.length > 4) {
                    try {
                        n = Math.max(1, Math.min(64, Integer.parseInt(a[4])));
                    } catch (NumberFormatException ignored) {
                        // cantidad por defecto
                    }
                }
                item = plugin.rods().baitItem(bd, n);
                name = bd.name();
            }
        }
        if (item == null) {
            plugin.send(s, "uso-dar");
            return;
        }
        giveTo(target, item);
        plugin.send(s, "item-dado", "item", name, "jugador", target.getName());
    }

    private void giveTo(Player p, ItemStack item) {
        p.getInventory().addItem(item).values()
                .forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        boolean adm = s.hasPermission("pesca.admin");
        if (a.length == 1) {
            List<String> out = new ArrayList<>(List.of("menu", "coleccion", "mercado", "tienda", "misiones",
                    "info", "vender", "limpiar", "torneo"));
            if (adm) out.addAll(List.of("reload", "heladera", "dar"));
            String pre = a[0].toLowerCase(Locale.ROOT);
            out.removeIf(x -> !x.startsWith(pre));
            return out;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2) {
            if (sub.equals("vender")) return List.of("mano");
            if (sub.equals("torneo")) return adm ? List.of("iniciar", "parar") : List.of();
            if (adm && (sub.equals("heladera") || sub.equals("dar"))) return null; // jugadores online
        }
        if (adm && sub.equals("dar")) {
            if (a.length == 3) return List.of("cana", "carnada");
            if (a.length == 4) {
                return new ArrayList<>(a[2].equalsIgnoreCase("carnada")
                        ? plugin.rods().baits().keySet() : plugin.rods().rods().keySet());
            }
        }
        return List.of();
    }
}
