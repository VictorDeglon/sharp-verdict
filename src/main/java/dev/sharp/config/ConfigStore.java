package dev.sharp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/**
 * Reads and writes {@link SharpConfig} as pretty-printed JSON.
 *
 * <ul>
 *   <li>A missing file is created with the defaults.
 *   <li>Missing keys keep their default and are written back, so new settings appear in old files.
 *   <li>A file from an older version (no or a lower {@code configVersion}) is copied to {@code sharp.json.v1.bak} and replaced with the
 *       current defaults, keeping only the switches in {@link SharpConfig#migratedFrom}.
 *   <li>Out-of-range values are clamped and written back.
 *   <li>A file that is not valid JSON, or has a value of the wrong type, is moved to
 *       {@code sharp.json.broken} (never silently deleted) and replaced with defaults.
 *   <li>Writes go to a temporary file first and are then moved into place, so a crash mid-write cannot
 *       leave a half-written config.
 * </ul>
 */
public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ConfigStore() {
    }

    public static SharpConfig load(Path file, Consumer<String> warn) {
        if (!Files.exists(file)) {
            SharpConfig defaults = new SharpConfig().sanitize();
            trySave(file, defaults, warn);
            return defaults;
        }

        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warn.accept("Could not read " + file + ": " + e.getMessage() + ". Using defaults for this session.");
            return new SharpConfig().sanitize();
        }

        SharpConfig config;
        JsonElement original;
        int fileVersion;
        try {
            original = JsonParser.parseString(text);
            if (!original.isJsonObject()) {
                throw new JsonParseException("top level value is not an object");
            }
            fileVersion = versionOf(original.getAsJsonObject());
            config = GSON.fromJson(original, SharpConfig.class);
            if (config == null) {
                throw new JsonParseException("empty config");
            }
        } catch (RuntimeException e) {
            // Deliberately broad: this is user-edited input going through a third-party parser, and depending
            // on the Gson version a bad value surfaces as JsonParseException, NumberFormatException,
            // IllegalStateException or ClassCastException. Whatever it is, a bad file must never crash startup.
            warn.accept("Config " + file + " is invalid (" + e.getMessage() + "). Keeping a copy as "
                    + file.getFileName() + ".broken and using defaults.");
            backUpBroken(file, warn);
            SharpConfig defaults = new SharpConfig().sanitize();
            trySave(file, defaults, warn);
            return defaults;
        }

        if (fileVersion < SharpConfig.CURRENT_VERSION) {
            warn.accept("Config " + file + " is from an older version (" + fileVersion + "). Keeping it as "
                    + file.getFileName() + ".v" + fileVersion + ".bak and applying the new defaults.");
            backUp(file, ".v" + fileVersion + ".bak", warn);
            config = SharpConfig.migratedFrom(config);
            trySave(file, config, warn);
            return config;
        }

        config.sanitize();
        // Rewrite if sanitising or filling in defaults changed anything, so the file always shows the full,
        // valid set of options.
        if (!JsonParser.parseString(GSON.toJson(config)).equals(original)) {
            trySave(file, config, warn);
        }
        return config;
    }

    public static void save(Path file, SharpConfig config) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(config) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** JSON text of a config, mainly for tests and the debug output. */
    public static String toJson(SharpConfig config) {
        return GSON.toJson(config);
    }

    private static void trySave(Path file, SharpConfig config, Consumer<String> warn) {
        try {
            save(file, config);
        } catch (IOException e) {
            warn.accept("Could not write " + file + ": " + e.getMessage());
        }
    }

    private static void backUpBroken(Path file, Consumer<String> warn) {
        Path backup = file.resolveSibling(file.getFileName() + ".broken");
        try {
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            warn.accept("Could not back up the broken config: " + e.getMessage());
        }
    }

    /** Copies (does not move) the file, so the original is still there if writing the new one fails. */
    private static void backUp(Path file, String suffix, Consumer<String> warn) {
        Path backup = file.resolveSibling(file.getFileName() + suffix);
        try {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            warn.accept("Could not back up the old config: " + e.getMessage());
        }
    }

    /**
     * The version a file declares. A file without one is a version 1 file when it has a key only version 1 wrote (every file the first
     * release saved has it), and otherwise a hand-written partial file, which is taken as current so its values are kept.
     */
    private static int versionOf(com.google.gson.JsonObject object) {
        JsonElement v = object.get("configVersion");
        if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
            return v.getAsInt();
        }
        return object.has("hitboxSelectedColor") ? 1 : SharpConfig.CURRENT_VERSION;
    }
}
