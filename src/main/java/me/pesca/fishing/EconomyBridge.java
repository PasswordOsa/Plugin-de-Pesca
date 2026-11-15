package me.pesca.fishing;

import org.bukkit.entity.Player;

public interface EconomyBridge {
    void deposit(Player player, PlayerData data, double amount);

    double balance(Player player, PlayerData data);

    /** @return false si no alcanza el dinero. */
    boolean withdraw(Player player, PlayerData data, double amount);

    String format(double amount);

    /** true si no hay Vault y se usa el saldo interno del plugin. */
    boolean internal();
}
