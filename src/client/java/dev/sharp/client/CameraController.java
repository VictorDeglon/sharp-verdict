package dev.sharp.client;

import dev.sharp.core.camera.AimStep;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Applies a camera step computed by the core to the player's view. It decides nothing about where to
 * look: that is {@code AimController}'s job, and which entity to look at is the target selector's. Keeping
 * this class this small is what lets either feature be switched off or tested on its own.
 */
final class CameraController {

    /** Rotates the view by {@code step}. Does nothing for missing, zero or invalid steps and while a screen is open. */
    void apply(Minecraft mc, AimStep step) {
        LocalPlayer player = mc.player;
        if (player == null || step == null || step.isZero() || mc.gui.screen() != null) {
            return;
        }
        if (!Double.isFinite(step.deltaYaw()) || !Double.isFinite(step.deltaPitch())) {
            return;
        }
        GameAccess.turn(player, step.deltaYaw(), step.deltaPitch());
    }
}
