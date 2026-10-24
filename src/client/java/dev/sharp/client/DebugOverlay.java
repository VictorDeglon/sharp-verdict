package dev.sharp.client;

import dev.sharp.core.debug.DebugFormatter;
import dev.sharp.core.debug.DebugSnapshot;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Small text panel in the top left corner with the live state: activation, target, angular error and
 * camera numbers. Switched on with the debug key or {@code debugOverlay} in the config.
 */
final class DebugOverlay {
    private static final int COLOR_ACTIVE = 0xFF8CFF8C;
    private static final int COLOR_IDLE = 0xFFFFFFFF;
    private static final int COLOR_BODY = 0xFFE0E0E0;

    private DebugOverlay() {
    }

    static void register(SharpRuntime runtime, ConfigManager configs) {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(SharpClient.MOD_ID, "debug"), (graphics, deltaTracker) -> {
            if (!configs.get().debugOverlay) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }
            DebugSnapshot snapshot = runtime.debugSnapshot();
            int x = 4;
            int y = 4;
            int lineHeight = mc.font.lineHeight + 2;
            if (snapshot == null) {
                graphics.text(mc.font, "SHARP debug: idle", x, y, COLOR_IDLE, true); // VERIFIED (GuiGraphicsExtractor#text)
                return;
            }
            List<String> lines = DebugFormatter.lines(snapshot);
            for (int i = 0; i < lines.size(); i++) {
                int color = i == 0 ? (snapshot.active() ? COLOR_ACTIVE : COLOR_IDLE) : COLOR_BODY;
                graphics.text(mc.font, lines.get(i), x, y + i * lineHeight, color, true);
            }
        });
    }
}
