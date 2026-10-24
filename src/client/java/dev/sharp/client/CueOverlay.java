package dev.sharp.client;

import dev.sharp.core.hud.Cues;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * The coloured status text above the hotbar while the key is held. What it says and in which colours is decided in the core
 * ({@code CueBuilder}); this class only lays the rows out centred, each on a dark strip so it reads over any background, and draws them.
 */
final class CueOverlay {
    /** Distance from the bottom of the screen to the bottom of the last row, in GUI pixels. Clears the action bar and the status bars. */
    private static final int BOTTOM_MARGIN = 72;
    private static final float HEADLINE_SCALE = 1.5f;
    /** Rows the block is laid out for: headline, detail and the colour key. */
    private static final int RESERVED_ROWS = 3;
    private static final int GAP = 3;
    private static final int STRIP = 0x99000000;
    /** The quiet single line is drawn at this fraction of its colour's opacity. */
    private static final float MINIMAL_ALPHA = 0.72f;
    /** Rows never get closer than this to the sides of the screen, in GUI pixels. */
    private static final int SIDE_MARGIN = 6;

    private CueOverlay() {
    }

    static void register(SharpRuntime runtime) {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(SharpClient.MOD_ID, "cues"), (graphics, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }
            Cues cues = runtime.cues();
            if (cues.isEmpty()) {
                return;
            }
            List<Cues.Line> lines = cues.lines();
            if (cues.minimal()) {
                // One dim line, no strip, in the place the status row of the full text would be.
                int y = graphics.guiHeight() - BOTTOM_MARGIN - mc.font.lineHeight;
                drawLine(graphics, mc.font, lines.get(0), graphics.guiWidth() / 2, y, 1.0f, false, MINIMAL_ALPHA);
                return;
            }
            int available = graphics.guiWidth() - 2 * SIDE_MARGIN;
            float[] scales = new float[lines.size()];
            int total = 0;
            for (int i = 0; i < lines.size(); i++) {
                int width = Math.max(1, textWidth(mc.font, lines.get(i)));
                // The headline is bigger, and every row shrinks if it would not fit the window.
                scales[i] = Math.min(i == 0 ? HEADLINE_SCALE : 1.0f, available / (float) width);
                total += Math.round(mc.font.lineHeight * scales[i]) + (i > 0 ? GAP : 0);
            }
            if (lines.size() < RESERVED_ROWS) {
                // The colour key comes and goes. Its row stays reserved so the rows above it never jump when it leaves.
                total += (RESERVED_ROWS - lines.size()) * (mc.font.lineHeight + GAP);
            }
            int centre = graphics.guiWidth() / 2;
            int y = graphics.guiHeight() - BOTTOM_MARGIN - total;
            for (int i = 0; i < lines.size(); i++) {
                drawLine(graphics, mc.font, lines.get(i), centre, y, scales[i], true, 1.0f);
                y += Math.round(mc.font.lineHeight * scales[i]) + GAP;
            }
        });
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.round((argb >>> 24) * alpha);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int textWidth(Font font, Cues.Line line) {
        int width = 0;
        for (Cues.Segment segment : line.segments()) {
            width += font.width(segment.text());
        }
        return width;
    }

    private static void drawLine(
            GuiGraphicsExtractor graphics, Font font, Cues.Line line, int centreX, int y, float scale, boolean strip, float alpha) {
        int width = textWidth(font, line);
        var pose = graphics.pose();
        pose.pushMatrix(); // UNVERIFIED: Matrix3x2fStack#pushMatrix
        pose.translate(centreX - width * scale / 2.0f, y);
        pose.scale(scale, scale);
        if (strip) {
            graphics.fill(-3, -2, width + 3, font.lineHeight + 1, STRIP); // VERIFIED: GuiGraphicsExtractor#fill(int x5)
        }
        int x = 0;
        for (Cues.Segment segment : line.segments()) {
            graphics.text(font, segment.text(), x, 0, withAlpha(segment.argb(), alpha), true);
            x += font.width(segment.text());
        }
        pose.popMatrix();
    }
}
