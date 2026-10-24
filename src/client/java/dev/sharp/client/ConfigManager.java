package dev.sharp.client;

import dev.sharp.config.ConfigStore;
import dev.sharp.config.SharpConfig;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

/** Owns the loaded settings and the file they live in ({@code config/sharp.json}). */
final class ConfigManager {
    private final Path file;
    private final Logger logger;
    private volatile SharpConfig current;

    private ConfigManager(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
        this.current = ConfigStore.load(file, logger::warn);
    }

    static ConfigManager create(Logger logger) {
        return new ConfigManager(FabricLoader.getInstance().getConfigDir().resolve("sharp.json"), logger);
    }

    /** The live settings. Read this fresh each time rather than keeping a reference. */
    SharpConfig get() {
        return current;
    }

    /** Re-reads the file, picking up edits made while the game was running. */
    void reload() {
        current = ConfigStore.load(file, logger::warn);
    }

    /** Writes the in-memory settings back, used after an in-game toggle. */
    void save() {
        try {
            ConfigStore.save(file, current);
        } catch (IOException e) {
            logger.warn("Could not save {}: {}", file, e.getMessage());
        }
    }
}
