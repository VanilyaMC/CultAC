package ac.cult.cultac.network;

import static ac.cult.cultac.network.JavaTickFixture.describe;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ac.cult.cultac.checks.impl.prediction.PredictionResult;
import ac.cult.cultac.checks.impl.prediction.checks.psuedo.ElytraPseudo;
import ac.cult.cultac.checks.impl.prediction.stage.uncertainty.ElytraTransform;
import ac.cult.cultac.utils.math.Vec3;
import ac.cult.cultac.utils.nmsutil.ReachUtils;
import org.junit.jupiter.api.Test;

/** GlideTolerance on a real Java glide tick: the Elytra flag stays on the result, only its enforcement passes. */
class JavaGlideToleranceTest {
    private static final double GRAVITY = 0.08;
    private static final float YAW = 30.0F, PITCH = -20.0F;
    private static final Vec3 FROM = new Vec3(8.5, JavaTickFixture.FLOOR_Y + 40, 8.5);
    private static final Vec3 CARRIED = new Vec3(0.5, -0.1, 0.8);

    @Test
    void aSmallGlideOffsetFlagsButIsTolerated() throws Exception {
        PredictionResult result = glideOffBy(new Vec3(0.06, 0.0, 0.04));
        assertTrue(result.hasFlag(ElytraPseudo.class), describe(result));
        assertTrue(result.isGlideTolerated(), describe(result));
    }

    @Test
    void aLargeGlideOffsetIsNotTolerated() throws Exception {
        PredictionResult result = glideOffBy(new Vec3(0.0, 0.6, 0.0));
        assertTrue(result.hasFlag(ElytraPseudo.class), describe(result));
        assertFalse(result.isGlideTolerated(), describe(result));
    }

    @Test
    void anExactGlideHasNothingToTolerate() throws Exception {
        PredictionResult result = glideOffBy(Vec3.ZERO);
        assertFalse(result.hasEffectiveFlags(), describe(result));
        assertFalse(result.isGlideTolerated(), describe(result));
    }

    private static PredictionResult glideOffBy(Vec3 extra) throws Exception {
        try (var f = new JavaTickFixture()) {
            f.player.isGliding = true;
            f.player.refreshPlayerPose();
            f.player.xRot = YAW;
            f.player.yRot = PITCH;
            f.place(FROM, CARRIED);
            Vec3 glide = ElytraTransform.getElytraMovement(
                    f.player, CARRIED, ReachUtils.getLook(f.player, YAW, PITCH), PITCH, GRAVITY);
            return f.tick(FROM.add(glide).add(extra), false);
        }
    }
}
