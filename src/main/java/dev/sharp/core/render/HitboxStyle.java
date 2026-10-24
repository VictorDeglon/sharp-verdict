package dev.sharp.core.render;

/**
 * Resolved look of one outlined box.
 *
 * @param outlineColor    ARGB colour of the outline
 * @param fillColor       ARGB colour of the fill; only used when {@code fill} is true
 * @param markerColor     ARGB colour of the aim point cross; only used when {@code aimMarker} is true
 * @param lineWidthPixels outline width in approximate screen pixels
 * @param fill            whether to draw the translucent fill
 * @param aimMarker       whether to draw the aim point cross
 */
public record HitboxStyle(int outlineColor, int fillColor, int markerColor, double lineWidthPixels, boolean fill, boolean aimMarker) {
}
