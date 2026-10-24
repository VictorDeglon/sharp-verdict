package dev.sharp.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sharp.core.math.Box;
import org.junit.jupiter.api.Test;

class BoxTrailTest {
    private static Box boxAt(double x) {
        return new Box(x, 0, 0, x + 1, 2, 1);
    }

    @Test
    void emptyTrailHasNoBox() {
        BoxTrail trail = new BoxTrail();
        assertTrue(trail.isEmpty());
        assertNull(trail.at(0.5));
    }

    @Test
    void firstObservationIsShownAsIsAtAnyPartialTick() {
        BoxTrail trail = new BoxTrail();
        trail.observe(100, boxAt(5));
        assertEquals(boxAt(5), trail.at(0.0));
        assertEquals(boxAt(5), trail.at(0.7));
    }

    @Test
    void blendsBetweenTheLastTwoTicks() {
        BoxTrail trail = new BoxTrail();
        trail.observe(100, boxAt(0));
        trail.observe(101, boxAt(10));
        assertEquals(boxAt(0), trail.at(0.0));
        assertEquals(boxAt(5), trail.at(0.5));
        assertEquals(boxAt(10), trail.at(1.0));
        assertEquals(boxAt(0), trail.at(-3.0), "partial tick is clamped");
        assertEquals(boxAt(10), trail.at(9.0), "partial tick is clamped");
    }

    @Test
    void sameTickRefreshesTheLatestValueWithoutShiftingHistory() {
        BoxTrail trail = new BoxTrail();
        trail.observe(100, boxAt(0));
        trail.observe(101, boxAt(10));
        trail.observe(101, boxAt(12));
        assertEquals(boxAt(0), trail.at(0.0));
        assertEquals(boxAt(12), trail.at(1.0));
    }

    @Test
    void movesSmoothlyAcrossManyFramesPerTick() {
        BoxTrail trail = new BoxTrail();
        double previous = -1;
        for (long tick = 0; tick < 5; tick++) {
            for (int frame = 0; frame < 6; frame++) {
                trail.observe(tick, boxAt(tick * 2.0));
                double partial = frame / 6.0;
                double x = trail.at(partial).minX();
                if (tick > 0) {
                    assertTrue(x >= previous - 1e-9, "went backwards at tick " + tick + " frame " + frame);
                    assertTrue(x - previous < 0.5, "jumped at tick " + tick + " frame " + frame);
                }
                previous = x;
            }
        }
    }

    @Test
    void skippingTicksRestartsInsteadOfSlidingAcrossTheGap() {
        BoxTrail trail = new BoxTrail();
        trail.observe(100, boxAt(0));
        trail.observe(101, boxAt(10));
        trail.observe(150, boxAt(500));
        assertEquals(boxAt(500), trail.at(0.0));
        assertEquals(boxAt(500), trail.at(1.0));
    }

    @Test
    void timeGoingBackwardsRestarts() {
        BoxTrail trail = new BoxTrail();
        trail.observe(100, boxAt(0));
        trail.observe(101, boxAt(10));
        trail.observe(5, boxAt(40));
        assertEquals(boxAt(40), trail.at(0.0));
    }

    @Test
    void resetEmptiesTheTrail() {
        BoxTrail trail = new BoxTrail();
        trail.observe(1, boxAt(1));
        trail.reset();
        assertTrue(trail.isEmpty());
        assertNull(trail.at(0.5));
    }
}
