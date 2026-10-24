package dev.sharp.core.targeting;

import dev.sharp.core.math.V3;

/** Answers whether nothing solid sits between two points. Implemented with a world raycast in game. */
@FunctionalInterface
public interface LineOfSight {
    LineOfSight ALWAYS = (from, to) -> true;

    boolean visible(V3 from, V3 to);
}
