package me.pesca.fishing;

import org.bukkit.Material;

public record FishType(String id, String name, Material material, String rarity, String color,
                       int minLevel, double chance, double price, int xp,
                       double minWeight, double maxWeight, long shelfLifeMs) {
    public double avgWeight() {
        return (minWeight + maxWeight) / 2.0;
    }
}
