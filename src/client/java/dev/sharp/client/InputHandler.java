package dev.sharp.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.sdl.SDLScancode;

/**
 * Key bindings. All of them appear under "SHARP" in the game's Controls screen, so they can be rebound
 * there. The hold key is read with {@link KeyMapping#isDown()}, the key's live state, never a queued
 * click, so it can only be true while the key is physically down.
 */
final class InputHandler {
    private final KeyMapping hold;
    private final KeyMapping toggleTracking;
    private final KeyMapping toggleDebug;
    private final KeyMapping cycleText;

    InputHandler() {
        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SharpClient.MOD_ID, "main"));
        hold = register("hold", SDLScancode.SDL_SCANCODE_G, category);
        toggleTracking = register("toggle_tracking", SDLScancode.SDL_SCANCODE_H, category);
        toggleDebug = register("toggle_debug", SDLScancode.SDL_SCANCODE_J, category);
        cycleText = register("cycle_text", SDLScancode.SDL_SCANCODE_K, category);
    }

    private static KeyMapping register(String name, int scancode, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key." + SharpClient.MOD_ID + "." + name, InputConstants.Type.KEYBOARD, scancode, category));
    }

    /** True exactly while the hold key is physically down (and the game is delivering input). */
    boolean holdDown() {
        return hold.isDown();
    }

    /** True once per press of the tracking toggle key. Always drains queued presses. */
    boolean consumeTrackingToggle() {
        return drain(toggleTracking);
    }

    /** True once per press of the debug toggle key. Always drains queued presses. */
    boolean consumeDebugToggle() {
        return drain(toggleDebug);
    }

    /** True once per press of the status text key. Always drains queued presses. */
    boolean consumeTextCycle() {
        return drain(cycleText);
    }

    private static boolean drain(KeyMapping key) {
        boolean pressed = false;
        while (key.consumeClick()) {
            pressed = true;
        }
        return pressed;
    }
}
