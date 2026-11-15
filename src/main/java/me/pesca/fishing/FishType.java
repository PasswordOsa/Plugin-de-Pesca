package me.pesca.fishing;

import org.bukkit.Material;

/**
 * time: 0 cualquiera, 1 dia, 2 noche.  weather: 0 cualquiera, 1 lluvia, 2 despejado.
 * zoneMask: bit por cada Zone.
 */
public record FishType(String id, String name, String desc, Material material, String rarity, String color,
                       int rank, int minLevel, double chance, double price, int xp,
                       double minWeight, double maxWeight, long shelfLifeMs,
                       int zoneMask, int time, int weather) {
    public double avgWeight() {
        return (minWeight + maxWeight) / 2.0;
    }

    public boolean inZone(Zone z) {
        return (zoneMask & (1 << z.ordinal())) != 0;
    }
}
