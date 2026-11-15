package me.pesca.fishing;

import org.bukkit.entity.Player;

import java.util.Locale;

public final class InternalEco implements EconomyBridge {
    @Override
    public void deposit(Player player, PlayerData data, double amount) {
        data.balance += amount;
        data.dirty = true;
    }

    @Override
    public String format(double amount) {
        return String.format(Locale.US, "$%,.2f", amount);
    }

    @Override
    public boolean internal() {
        return true;
    }
}
