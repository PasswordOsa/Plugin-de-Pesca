package me.pesca.fishing;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Cache en memoria + guardado asincrono en un unico hilo (nunca bloquea el hilo principal). */
public final class DataManager {
    private final PescaPlugin plugin;
    private final File dir;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PescaPlus-IO");
        t.setDaemon(true);
        return t;
    });

    public DataManager(PescaPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "data");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    /** Carga (o devuelve de cache). Seguro de llamar en hilos asincronos. */
    public PlayerData get(UUID id) {
        return cache.computeIfAbsent(id, this::read);
    }

    private PlayerData read(UUID id) {
        try {
            // pasa por el mismo hilo de IO para leer despues de cualquier escritura pendiente
            return io.submit(() -> readFile(id)).get();
        } catch (Exception ex) {
            return readFile(id);
        }
    }

    private PlayerData readFile(UUID id) {
        File f = file(id);
        if (!f.exists()) return new PlayerData(id);
        return PlayerData.fromYaml(id, YamlConfiguration.loadConfiguration(f));
    }

    public void unload(UUID id) {
        PlayerData d = cache.remove(id);
        if (d != null && d.dirty) saveAsync(d);
    }

    public void autosave() {
        for (PlayerData d : cache.values()) if (d.dirty) saveAsync(d);
    }

    private void saveAsync(PlayerData d) {
        String yaml = d.toYaml(); // snapshot en el hilo principal (barato)
        d.dirty = false;
        File f = file(d.uuid);
        try {
            io.execute(() -> write(f, yaml));
        } catch (RuntimeException ex) {
            write(f, yaml);
        }
    }

    public void shutdown() {
        io.shutdown();
        try {
            io.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (PlayerData d : cache.values()) {
            if (d.dirty) {
                write(file(d.uuid), d.toYaml());
                d.dirty = false;
            }
        }
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    private void write(File f, String yaml) {
        try {
            Path target = f.toPath();
            Path tmp = target.resolveSibling(f.getName() + ".tmp");
            Files.writeString(tmp, yaml, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar " + f.getName() + ": " + ex.getMessage());
        }
    }
}
