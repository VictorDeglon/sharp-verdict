package dev.sharp.core.render;

import dev.sharp.core.math.Box;

/** One solid box to draw, in world coordinates, with its ARGB colour. */
public record ColoredBox(Box box, int argb) {
}
