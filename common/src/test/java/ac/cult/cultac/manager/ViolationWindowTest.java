package ac.cult.cultac.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ac.cult.cultac.checks.Check;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ViolationWindowTest {
    private final Check alpha = Mockito.mock(Check.class);
    private final Check beta = Mockito.mock(Check.class);

    @Test
    void aSecondFlagInTheSameMillisecondReplacesTheFirst() {
        ViolationWindow window = new ViolationWindow();
        window.add(1_000, alpha);
        window.add(1_000, beta);

        assertEquals(1, window.size());
        assertEquals(0, window.count(alpha));
        assertEquals(1, window.count(beta));
    }

    @Test
    void expiryKeepsViolationsExactlyAtTheLimit() {
        ViolationWindow window = new ViolationWindow();
        window.add(1_000, alpha);
        window.add(1_500, alpha);
        window.add(2_000, beta);

        window.expireBefore(2_500, 1_000);
        assertEquals(2, window.size());
        assertEquals(1, window.count(alpha));

        window.expireBefore(3_001, 1_000);
        assertEquals(0, window.size());
        assertEquals(0, window.count(alpha));
        assertEquals(0, window.count(beta));
    }

    /** Matches the map with a full removeIf scan it replaces, including a clock that steps back. */
    @Test
    void matchesTheFormerMapAndScanOverRandomFlags() {
        List<Check> checks = List.of(alpha, beta, Mockito.mock(Check.class));
        Random random = new Random(7);
        for (int run = 0; run < 50; run++) {
            Long2ObjectMap<Check> former = new Long2ObjectOpenHashMap<>();
            ViolationWindow window = new ViolationWindow();
            long now = 10_000;
            long maxAge = 1 + random.nextInt(400);
            for (int flag = 0; flag < 2_000; flag++) {
                // mostly forward, sometimes the same millisecond, sometimes a step back
                now += random.nextInt(10) == 0 ? -random.nextInt(50) : random.nextInt(4);
                Check check = checks.get(random.nextInt(checks.size()));
                long currentTime = now;

                former.put(currentTime, check);
                former.long2ObjectEntrySet().removeIf(time -> currentTime - time.getLongKey() > maxAge);
                window.add(currentTime, check);
                window.expireBefore(currentTime, maxAge);

                assertEquals(former.size(), window.size());
                for (Check each : checks) {
                    int expected = 0;
                    for (Check value : former.values()) {
                        if (value == each) expected++;
                    }
                    assertEquals(expected, window.count(each));
                }
            }
        }
    }
}
