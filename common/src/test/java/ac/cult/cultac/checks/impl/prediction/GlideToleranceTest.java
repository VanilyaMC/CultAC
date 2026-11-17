package ac.cult.cultac.checks.impl.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ac.cult.cultac.checks.impl.prediction.GlideTolerance.Settings;
import org.junit.jupiter.api.Test;

/** Which elytra, riptide and landing offsets GlideTolerance lets pass without a Simulation flag or a setback. */
class GlideToleranceTest {
    private static final Settings DEFAULTS = Settings.DEFAULTS;

    /** The live Elytra offsets of one flight (2026-11-15 19:13), 14 ticks fading by the glide drag. */
    private static final double[] LIVE_FLIGHT = {
        0.109617, 0.091554, 0.080240, 0.076681, 0.075061, 0.073474, 0.071920, 0.070399, 0.068908, 0.067449, 0.066020,
        0.064620, 0.063250, 0.061743
    };

    @Test
    void aLiveFlightThatWasSetBackIsTolerated() {
        GlideTolerance tolerance = new GlideTolerance();
        for (double offset : LIVE_FLIGHT) {
            assertTrue(tolerance.tolerate(true, true, offset, DEFAULTS), "offset " + offset);
        }
    }

    @Test
    void aLargeOffsetInFlightStillFlags() {
        GlideTolerance tolerance = new GlideTolerance();
        assertFalse(tolerance.tolerate(true, true, 1.490047, DEFAULTS));
        assertFalse(tolerance.tolerate(true, true, 0.619614, DEFAULTS));
        assertFalse(tolerance.tolerate(true, true, DEFAULTS.tolerance(), DEFAULTS));
    }

    /** A client that adds speed in flight keeps an offset that does not fade; the budget runs out within a second. */
    @Test
    void aSteadyBoostRunsOutOfBudgetAndFlags() {
        GlideTolerance tolerance = new GlideTolerance();
        int tolerated = 0;
        for (int tick = 0; tick < 20; tick++) {
            if (tolerance.tolerate(true, true, 0.2, DEFAULTS)) tolerated++;
        }
        // 0.2 + 0.15 a tick stays within 1.5 for 9 ticks; every tick after that flags.
        assertEquals(9, tolerated);
        assertFalse(tolerance.tolerate(true, true, 0.2, DEFAULTS));
    }

    @Test
    void theBudgetComesBackWhileTheFlightIsClean() {
        GlideTolerance tolerance = new GlideTolerance();
        while (tolerance.tolerate(true, true, 0.2, DEFAULTS)) {}
        for (int tick = 0; tick < 40; tick++) tolerance.tolerate(true, false, 0.0, DEFAULTS);
        assertTrue(tolerance.tolerate(true, true, 0.2, DEFAULTS));
    }

    @Test
    void theTicksJustAfterLandingAreTolerated() {
        GlideTolerance tolerance = new GlideTolerance();
        tolerance.tolerate(true, false, 0.0, DEFAULTS);
        for (int tick = 1; tick <= DEFAULTS.ticksAfter(); tick++) {
            assertTrue(tolerance.tolerate(false, true, 0.05, DEFAULTS), "tick " + tick + " after landing");
        }
        assertFalse(tolerance.tolerate(false, true, 0.05, DEFAULTS), "past the landing window");
    }

    @Test
    void walkingWithoutAFlightIsNotTolerated() {
        GlideTolerance tolerance = new GlideTolerance();
        assertFalse(tolerance.tolerate(false, true, 0.01, DEFAULTS));
    }

    /** NoFall, knockback or a step flag on the same tick keeps the whole tick flagged. */
    @Test
    void otherFlagsOnTheTickAreNotTolerated() {
        GlideTolerance tolerance = new GlideTolerance();
        assertFalse(tolerance.tolerate(true, false, 0.01, DEFAULTS));
    }

    @Test
    void aRespawnEndsTheFlight() {
        GlideTolerance tolerance = new GlideTolerance();
        tolerance.tolerate(true, false, 0.0, DEFAULTS);
        tolerance.reset();
        assertFalse(tolerance.tolerate(false, true, 0.05, DEFAULTS));
    }
}
