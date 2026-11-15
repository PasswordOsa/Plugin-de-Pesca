package me.pesca.fishing;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

public final class PescaPlugin extends JavaPlugin {
    private FishRegistry registry;
    private FishItems items;
    private RodItems rods;
    private DataManager data;
    private Market market;
    private ServerRecords records;
    private QuestManager quests;
    private Tournament tournament;
    private FishingListener fishing;
    private EconomyBridge economy = new InternalEco();
    private final Map<String, String> messages = new HashMap<>();
    private final Map<UUID, Menu> menus = new HashMap<>();
    private long[] xpCurve = new long[0];
    private int maxLevel = 50;
    private double sellBonusPerLevel;
    private double vanillaLootChance;
    private BukkitTask refreshTask, autosaveTask, marketTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registry = new FishRegistry();
        items = new FishItems(this, registry);
        rods = new RodItems(this);
        data = new DataManager(this);
        market = new Market(this);
        records = new ServerRecords();
        quests = new QuestManager(this);
        tournament = new Tournament(this);
        fishing = new FishingListener(this);
        loadSettings();

        market.load(data.loadFile("mercado.yml"));
        records.load(data.loadFile("records.yml"));

        getServer().getPluginManager().registerEvents(new PescaListener(this), this);
        getServer().getPluginManager().registerEvents(fishing, this);
        PluginCommand cmd = getCommand("pesca");
        if (cmd != null) {
            PescaCommand pc = new PescaCommand(this);
            cmd.setExecutor(pc);
            cmd.setTabCompleter(pc);
        }
        registerRecipe();
        scheduleTasks();

        // Vault: se resuelve en el primer tick, cuando ya cargaron todos los plugins de economia
        getServer().getScheduler().runTask(this, this::setupEconomy);

        for (Player p : getServer().getOnlinePlayers()) data.get(p.getUniqueId()); // por /reload
    }

    @Override
    public void onDisable() {
        if (refreshTask != null) refreshTask.cancel();
        if (autosaveTask != null) autosaveTask.cancel();
        if (marketTask != null) marketTask.cancel();
        if (tournament != null) tournament.shutdown();
        if (fishing != null) fishing.shutdown();
        if (data != null) {
            saveServerData();
            data.shutdown();
        }
    }

    // ------------------------------------------------------------ configuracion

    private void loadSettings() {
        reloadConfig();
        registry.load(getConfig(), getLogger());
        items.reload(getConfig());
        rods.load(getConfig());
        market.reload(getConfig());
        quests.load(getConfig());
        fishing.reload();

        messages.clear();
        ConfigurationSection ms = getConfig().getConfigurationSection("mensajes");
        if (ms != null) for (String k : ms.getKeys(false)) messages.put(k, ms.getString(k, ""));

        maxLevel = Math.max(1, getConfig().getInt("nivel.maximo", 50));
        double base = getConfig().getDouble("nivel.xp-base", 100);
        double exp = getConfig().getDouble("nivel.exponente", 1.5);
        xpCurve = new long[maxLevel + 1];
        for (int l = 1; l <= maxLevel; l++) xpCurve[l] = Math.max(1L, Math.round(base * Math.pow(l, exp)));
        sellBonusPerLevel = getConfig().getDouble("nivel.bonus-venta-por-nivel", 0.01);
        vanillaLootChance = Math.min(1.0, Math.max(0.0, getConfig().getDouble("loot-vanilla-chance", 0.0)));
    }

    public void reloadAll() {
        loadSettings();
        scheduleTasks();
    }

    private void scheduleTasks() {
        if (refreshTask != null) refreshTask.cancel();
        if (autosaveTask != null) autosaveTask.cancel();
        if (marketTask != null) marketTask.cancel();
        long period = Math.max(5L, getConfig().getLong("frescura.refresco-segundos", 30)) * 20L;
        refreshTask = getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : getServer().getOnlinePlayers()) refreshInventory(p);
        }, period, period);
        long save = Math.max(1L, getConfig().getLong("autoguardado-minutos", 5)) * 1200L;
        autosaveTask = getServer().getScheduler().runTaskTimer(this, () -> {
            data.autosave();
            saveServerData();
        }, save, save);
        marketTask = getServer().getScheduler().runTaskTimer(this, market::tick, 100L, 1200L);
        tournament.schedule();
    }

    private void saveServerData() {
        if (market.dirty()) data.saveFileAsync("mercado.yml", market.toYaml());
        if (records.dirty()) data.saveFileAsync("records.yml", records.toYaml());
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            try {
                EconomyBridge b = VaultEco.tryCreate(this);
                if (b != null) {
                    economy = b;
                    getLogger().info("Economía: usando Vault.");
                    return;
                }
            } catch (Throwable t) {
                getLogger().warning("Error enlazando con Vault: " + t.getMessage());
            }
        }
        getLogger().warning("Sin economía de Vault: se usa un saldo interno (ver /pesca info).");
    }

    private void registerRecipe() {
        if (!getConfig().getBoolean("heladera.receta", true)) return;
        ShapedRecipe r = new ShapedRecipe(new NamespacedKey(this, "heladera"), items.fridgeItem(1));
        r.shape("III", "IBI", "IPI");
        r.setIngredient('I', Material.IRON_INGOT);
        r.setIngredient('B', Material.BARREL);
        r.setIngredient('P', Material.PACKED_ICE);
        getServer().addRecipe(r);
    }

    // ------------------------------------------------------------ utilidades

    public void refreshInventory(Player p) {
        PlayerInventory inv = p.getInventory();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.hasItemMeta() && items.refresh(it, now)) inv.setItem(i, it);
        }
    }

    public void addXp(Player p, PlayerData d, long xp) {
        d.dirty = true;
        if (d.level >= maxLevel) {
            d.xp = 0;
            return;
        }
        d.xp += xp;
        boolean up = false;
        while (d.level < maxLevel && d.xp >= xpCurve[d.level]) {
            d.xp -= xpCurve[d.level];
            d.level++;
            up = true;
            announceUnlocks(p, d.level);
        }
        if (d.level >= maxLevel) d.xp = 0;
        if (up) {
            p.sendMessage(msg("nivel-subio", "nivel", d.level));
            p.sendTitle(color("&6¡Nivel " + d.level + "!"), color("&7Pesca"), 5, 40, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
    }

    private void announceUnlocks(Player p, int level) {
        StringJoiner sj = new StringJoiner(", ");
        for (FishType t : registry.all()) if (t.minLevel() == level) sj.add(color(t.color() + t.name() + "&7"));
        if (sj.length() > 0) p.sendMessage(msg("desbloqueo", "peces", sj.toString()));
    }

    public long xpToNext(int level) {
        return level >= maxLevel ? 0L : xpCurve[level];
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /** Mensaje sin prefijo ni color aplicado (para insertarlo dentro de otro). */
    public String raw(String key) {
        return messages.getOrDefault(key, "");
    }

    public String msg(String key, Object... kv) {
        String m = messages.getOrDefault(key, key);
        for (int i = 0; i + 1 < kv.length; i += 2) m = m.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        return color(messages.getOrDefault("prefijo", "") + m);
    }

    public void send(CommandSender to, String key, Object... kv) {
        to.sendMessage(msg(key, kv));
    }

    // ------------------------------------------------------------ accesores

    public FishRegistry registry() { return registry; }
    public FishItems items() { return items; }
    public RodItems rods() { return rods; }
    public DataManager data() { return data; }
    public Market market() { return market; }
    public ServerRecords records() { return records; }
    public QuestManager quests() { return quests; }
    public Tournament tournament() { return tournament; }
    public FishingListener fishing() { return fishing; }
    public EconomyBridge economy() { return economy; }
    public Map<UUID, Menu> menus() { return menus; }
    public int maxLevel() { return maxLevel; }
    public double sellBonusPerLevel() { return sellBonusPerLevel; }
    public double vanillaLootChance() { return vanillaLootChance; }
}
