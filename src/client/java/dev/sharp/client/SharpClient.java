package dev.sharp.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point. Creates the pieces and connects them to the game's events; no logic lives here.
 *
 * <ul>
 *   <li>{@link ConfigManager}: settings file.
 *   <li>{@link InputHandler}: key bindings.
 *   <li>{@link CameraController}: applies camera rotation.
 *   <li>{@link SharpRuntime}: runs the controller once per frame and once per tick.
 *   <li>{@link HitboxRenderer} and {@link DebugOverlay}: draw.
 * </ul>
 */
public final class SharpClient implements ClientModInitializer {
    public static final String MOD_ID = "sharp";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static SharpRuntime runtime;
    private static ConfigManager configs;

    /** The running feature, or null before the mod has started. For {@link SharpTestHooks}. */
    static SharpRuntime runtime() {
        return runtime;
    }

    static ConfigManager configs() {
        return configs;
    }

    @Override
    public void onInitializeClient() {
        ConfigManager configs = ConfigManager.create(LOGGER);
        InputHandler input = new InputHandler();
        CameraController camera = new CameraController();
        SharpRuntime runtime = new SharpRuntime(configs, input, camera, LOGGER);
        SharpClient.runtime = runtime;
        SharpClient.configs = configs;

        // Once per rendered frame: activation, selection, camera step, scene for the renderer.
        LevelExtractionEvents.END_EXTRACTION.register(runtime::onFrame);
        // Once per tick, including on screens with no world: the safety poll that switches the feature off.
        ClientTickEvents.END_CLIENT_TICK.register(runtime::onTick);

        // Hard resets, so no target state can outlive a world or a connection.
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> runtime.onJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> runtime.onDisconnect());
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> runtime.onLevelChange());

        HitboxRenderer.register(runtime, configs);
        DebugOverlay.register(runtime, configs);
        CueOverlay.register(runtime);

        LOGGER.info("SHARP: Verdict loaded. Hold the lock key (default G) to lock onto the best target in range.");
    }
}
