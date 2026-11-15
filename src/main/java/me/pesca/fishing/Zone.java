package me.pesca.fishing;

import org.bukkit.Location;

import java.util.Locale;

/** Zonas de pesca agrupadas a partir del bioma del anzuelo. */
public enum Zone {
    OCEANO("Océano"),
    OCEANO_PROFUNDO("Océano profundo"),
    RIO("Río"),
    PANTANO("Pantano"),
    FRIO("Zona fría"),
    JUNGLA("Jungla"),
    OTRO("Otras zonas");

    private final String display;

    Zone(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    public static Zone of(String biomeKey) {
        String k = biomeKey.toLowerCase(Locale.ROOT);
        if (k.contains("river")) return RIO;
        if (k.contains("deep") && k.contains("ocean")) return OCEANO_PROFUNDO;
        if (k.contains("ocean")) return (k.contains("frozen") || k.contains("cold")) ? FRIO : OCEANO;
        if (k.contains("swamp") || k.contains("mangrove")) return PANTANO;
        if (k.contains("frozen") || k.contains("snowy") || k.contains("ice") || k.contains("grove")) return FRIO;
        if (k.contains("jungle") || k.contains("bamboo")) return JUNGLA;
        return OTRO;
    }

    public static Zone at(Location loc) {
        return of(loc.getBlock().getBiome().getKey().getKey());
    }
}
