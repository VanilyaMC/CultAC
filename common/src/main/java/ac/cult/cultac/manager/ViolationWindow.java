package ac.cult.cultac.manager;

import ac.cult.cultac.checks.Check;
import it.unimi.dsi.fastutil.longs.Long2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

/**
 * A punishment group's recent violations: one check per millisecond timestamp, as the map it replaces kept them (a
 * second flag in the same millisecond replaces the first). The timestamps are kept sorted, so expiry removes only the
 * expired head instead of scanning every violation, and a count per check answers {@link #count} without iterating.
 * Not thread-safe; a group belongs to one player's punishment manager.
 */
final class ViolationWindow {
    private final Long2ObjectRBTreeMap<Check> byTime = new Long2ObjectRBTreeMap<>();
    private final Reference2IntOpenHashMap<Check> counts = new Reference2IntOpenHashMap<>();

    /** Records {@code check} at {@code time}, replacing the check already recorded at that millisecond. */
    void add(long time, Check check) {
        Check replaced = byTime.put(time, check);
        if (replaced != null) decrement(replaced);
        counts.addTo(check, 1);
    }

    /** Removes every violation with {@code now - time > maxAge}, as the former full scan did. */
    void expireBefore(long now, long maxAge) {
        while (!byTime.isEmpty()) {
            long oldest = byTime.firstLongKey();
            if (now - oldest <= maxAge) return;
            decrement(byTime.remove(oldest));
        }
    }

    int size() {
        return byTime.size();
    }

    /** The violations of {@code check} in the window, compared by identity. */
    int count(Check check) {
        return counts.getInt(check);
    }

    private void decrement(Check check) {
        if (counts.addTo(check, -1) == 1) counts.removeInt(check);
    }
}
