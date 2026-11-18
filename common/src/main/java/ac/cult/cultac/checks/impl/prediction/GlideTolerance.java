package ac.cult.cultac.checks.impl.prediction;

import ac.cult.cultac.CultAPI;
import ac.cult.cultac.checks.Check;
import ac.cult.cultac.checks.impl.prediction.checks.psuedo.ElytraPseudo;
import ac.cult.cultac.checks.impl.prediction.checks.psuedo.Speed;
import ac.cult.cultac.checks.impl.prediction.checks.psuedo.Strafe;
import ac.cult.cultac.checks.impl.prediction.checks.psuedo.VerticalOffset;
import ac.cult.cultac.player.CultPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import java.util.List;

/**
 * Small movement offsets while fall flying (elytra, also with a firework boost or levitation), in a riptide spin, and
 * in the first ticks after either ends do not flag.
 *
 * <p>Live data (2026-11-13 to 2026-11-17): every Elytra flag came with a Simulation flag on the same tick, with
 * offsets from 0.002 to 0.22 that fade by the glide drag, the shape of a velocity the client carries and the
 * prediction lost, not of a client that adds speed. At {@code immediate-setback-threshold: 0.1} and
 * {@code max-advantage: 1} such a flight was set back several times, and the Simulation flags put players under an
 * automatic control.
 *
 * <p>A tick passes only when all its flags are offset flags (Elytra, VerticalOffset, Speed, Strafe), its offset is
 * below {@code Simulation.glide-tolerance}, and the passed offsets, leaking {@code glide-tolerance-leak} a tick, stay
 * within {@code glide-tolerance-budget}. A client that adds speed in flight goes over one of the two within ticks and
 * flags as before; ElytraA-J (gliding without a jump, from the ground, too often) are not touched.
 *
 * <p>This is enforcement lenience, not an engine fix: the flags stay on the result, so the engine and its tests see
 * the same verdicts; {@link FlagCaller} and {@link OffsetHandler} skip a passed tick. The source of the offsets is
 * still to be found with a packet log of a real flight.
 */
public final class GlideTolerance {
    public static final double DEFAULT_TOLERANCE = 0.25;
    public static final double DEFAULT_BUDGET = 1.5;
    public static final double DEFAULT_LEAK = 0.05;
    public static final int DEFAULT_TICKS_AFTER = 5;

    private static final List<Class<? extends Check>> OFFSET_FLAGS =
            List.of(ElytraPseudo.class, VerticalOffset.class, Speed.class, Strafe.class);

    /** The tolerance settings, from the {@code Simulation} section. */
    public record Settings(double tolerance, double budget, double leak, int ticksAfter) {
        public static final Settings DEFAULTS =
                new Settings(DEFAULT_TOLERANCE, DEFAULT_BUDGET, DEFAULT_LEAK, DEFAULT_TICKS_AFTER);

        static Settings from(ConfigManager config) {
            return new Settings(
                    config.getDoubleElse("Simulation.glide-tolerance", DEFAULT_TOLERANCE),
                    config.getDoubleElse("Simulation.glide-tolerance-budget", DEFAULT_BUDGET),
                    config.getDoubleElse("Simulation.glide-tolerance-leak", DEFAULT_LEAK),
                    config.getIntElse("Simulation.glide-tolerance-ticks-after", DEFAULT_TICKS_AFTER));
        }
    }

    private int ticksSinceGlide = Integer.MAX_VALUE;
    private double used;
    private boolean exhausted;
    private Settings settings = Settings.DEFAULTS;

    /**
     * One predicted movement tick. {@code glideTick}: the tick was fall flying or in a riptide spin.
     * {@code onlyOffsetFlags}: the tick flagged, with offset flags only. Returns whether the flags are tolerated.
     */
    public boolean tolerate(boolean glideTick, boolean onlyOffsetFlags, double offset, Settings settings) {
        if (glideTick) {
            ticksSinceGlide = 0;
        } else if (ticksSinceGlide != Integer.MAX_VALUE) {
            ticksSinceGlide++;
        }
        used = Math.max(0.0D, used - settings.leak());
        if (used == 0.0D) exhausted = false;
        if (!onlyOffsetFlags || ticksSinceGlide > settings.ticksAfter()) return false;
        if (exhausted || !(offset < settings.tolerance())) return false;
        if (used + offset > settings.budget()) {
            // Spent: nothing more passes until the tolerated offsets have leaked away.
            exhausted = true;
            return false;
        }
        used += offset;
        return true;
    }

    /** A respawn or a new level: the last flight is over. */
    public void reset() {
        ticksSinceGlide = Integer.MAX_VALUE;
        used = 0.0D;
        exhausted = false;
    }

    /** Whether {@code check} is one of the offset flags a glide tick may pass. */
    public static boolean isOffsetFlag(Check check) {
        return OFFSET_FLAGS.contains(check.getClass());
    }

    /** Marks the selected prediction of a Java movement as passed when its flags are tolerated. */
    public void apply(CultPlayer player, PredictionResult result) {
        SimulationContext context = result.getSimulationContext();
        if (context == null || result.isTeleport) return;
        boolean glideTick = context.getVehicle() == null
                && (context.usesFallFlyingMovement() && !context.getWorldData().isInLiquid()
                        || context.getRiptideLevel() > 0
                        || player.riptideSpinAttackTicks > 0);
        List<PredictionResult.Flag> flags = result.getFlags();
        boolean flagged = !result.isExempt() && result.getFlagSeverity() > 0.0D && !flags.isEmpty();
        boolean onlyOffsetFlags = flagged;
        double offset = 0.0D;
        if (flagged) {
            offset = result.getOffset();
            for (PredictionResult.Flag flag : flags) {
                if (!isOffsetFlag(flag.getCheck())) {
                    onlyOffsetFlags = false;
                    break;
                }
                offset = Math.max(offset, flag.getSeverity());
            }
        }
        // Read only on a flagged tick; the leak of the ticks between uses the settings last read.
        if (onlyOffsetFlags)
            settings = Settings.from(CultAPI.INSTANCE.getConfigManager().getConfig());
        if (tolerate(glideTick, onlyOffsetFlags, offset, settings)) result.setGlideTolerated(true);
    }
}
