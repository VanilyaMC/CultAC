package ac.cult.cultac.checks.impl.autoclicker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ac.cult.cultac.utils.math.CultMath;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The click rate AutoclickerLimit compares with max-cps (25 by default), over its batches of 50 swings. */
class AutoclickerCpsTest {
    private static final int SAMPLES = 50;
    private static final double MAX_CPS = 25;

    /** Arrival times of 50 swings delivered together, one millisecond apart. */
    private static List<Long> deliveredTogether(long start) {
        List<Long> millis = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) millis.add(start + i);
        return millis;
    }

    private static List<Long> evenly(long start, double period) {
        List<Long> values = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) values.add(start + Math.round(i * period));
        return values;
    }

    /** A 12 CPS player whose swings arrive in one burst after a lag spike was 2500 CPS by arrival times alone. */
    @Test
    void aLagBurstOfLegitClicksIsMeasuredByTheClientTicks() {
        List<Long> millis = deliveredTogether(10_000);
        List<Long> ticks = evenly(100, 20.0 / 12.0);

        double cps = CultMath.getCps(millis, ticks);

        assertTrue(CultMath.getCps(millis) > 1000, "arrival times alone");
        assertEquals(12, cps, 0.3);
        assertTrue(cps < MAX_CPS);
    }

    /** A client below 20 ticks per second clicks more per tick; its arrival times still show the real rate. */
    @Test
    void aSlowClientIsMeasuredByArrivalTimes() {
        List<Long> millis = evenly(10_000, 1000.0 / 14.0);
        List<Long> ticks = evenly(100, 10.0 / 14.0); // the client ran 10 ticks per second

        double cps = CultMath.getCps(millis, ticks);

        assertEquals(14, cps, 0.3);
        assertTrue(cps < MAX_CPS);
    }

    /** A steady high-ping player is measured the same by both clocks. */
    @Test
    void aSteadyLegitPlayerStaysBelowTheLimit() {
        double cps = CultMath.getCps(evenly(50_000, 1000.0 / 16.0), evenly(400, 20.0 / 16.0));

        assertEquals(16, cps, 0.3);
    }

    /** An autoclicker at 30 CPS is over the limit on both clocks, so neither span can hide it. */
    @Test
    void anAutoclickerIsOverTheLimit() {
        double cps = CultMath.getCps(evenly(10_000, 1000.0 / 30.0), evenly(100, 20.0 / 30.0));

        assertEquals(30, cps, 0.6);
        assertTrue(cps > MAX_CPS);
    }

    /** A burst-delivered autoclicker cannot use the bunching either: its ticks are as close as its clicks. */
    @Test
    void aBurstDeliveredAutoclickerIsOverTheLimit() {
        double cps = CultMath.getCps(deliveredTogether(10_000), evenly(100, 20.0 / 40.0));

        assertTrue(cps > MAX_CPS, "cps " + cps);
    }

    /** Fifty swings in one client tick and one millisecond are no click rate a player can produce. */
    @Test
    void swingsWithNoSpanAreUnbounded() {
        List<Long> same = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) same.add(5L);

        assertEquals(Double.POSITIVE_INFINITY, CultMath.getCps(same, same));
    }

    /** The rate counts the intervals between clicks: 50 clicks 50 ms apart are 20 CPS, not 20.4. */
    @Test
    void countsIntervalsNotClicks() {
        assertEquals(20, CultMath.getCps(evenly(0, 50), evenly(0, 1)), 1e-9);
    }
}
