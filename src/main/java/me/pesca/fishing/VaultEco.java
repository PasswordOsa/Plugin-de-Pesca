package me.pesca.fishing;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Esta clase solo se carga si Vault existe (se instancia dentro de un try/catch en PescaPlugin). */
public final class VaultEco implements EconomyBridge {
    private final Economy eco;

    private VaultEco(Economy eco) {
        this.eco = eco;
    }

    public static EconomyBridge tryCreate(Plugin plugin) {
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : new VaultEco(rsp.getProvider());
    }

    @Override
    public void deposit(Player player, PlayerData data, double amount) {
        eco.depositPlayer(player, amount);
    }

    @Override
    public String format(double amount) {
        return eco.format(amount);
    }

    @Override
    public boolean internal() {
        return false;
    }
}
