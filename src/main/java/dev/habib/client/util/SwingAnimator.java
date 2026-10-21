package dev.habib.client.util;

import dev.habib.client.config.HabibConfig;
import dev.habib.client.beta.BetaAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Replaces the first-person swing with a smooth "dip": the item comes down and returns,
 * instead of swinging outward. Applies to sword swings and to right-click ender pearl throws, using the same
 * style for both; every other item keeps the vanilla animation, as does style 0.
 */
public final class SwingAnimator {
    public static final String[] NAMES = {"Vanilla", "Smooth Dip", "Soft Tap", "Low Chop", "Flat Stab"};
    public static final int COUNT = NAMES.length;

    /** Per style: {down (y), forward push (z), tilt in degrees about X}. Peak values at 100% intensity. */
    private static final float[][] STYLE = {
            {0f, 0f, 0f},
            {-0.30f, -0.04f, -10f},   // Smooth Dip
            {-0.12f, -0.10f, -6f},    // Soft Tap
            {-0.34f, -0.08f, -35f},   // Low Chop
            {0f, 0f, 0f},             // Flat Stab (own two-phase motion, see flatStab)
    };

    private SwingAnimator() {}

    /** True if the last click was a right-click use (set by MinecraftClientMixin). */
    private static volatile boolean lastClickWasUse = false;
    private static volatile boolean lastClickWasAnchorUse = false;

    public static void markUse(boolean anchorUse) {
        lastClickWasUse = true;
        lastClickWasAnchorUse = anchorUse;
    }

    /** True if the last left-click attack had an entity (a hitbox) under the crosshair. */
    private static volatile boolean lastAttackHit = false;

    public static void markAttack(boolean hitEntity) {
        lastClickWasUse = false;
        lastClickWasAnchorUse = false;
        lastAttackHit = hitEntity;
    }

    /** True for a sword swing (only ones that hit, if that option is on), or a right-click ender pearl throw, in the swinging hand. */
    private static boolean animatedSwing(Arm arm) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return false;
        ItemStack stack = arm == player.getMainArm() ? player.getMainHandStack() : player.getOffHandStack();
        if (HabibConfig.anchorOptimizer && lastClickWasUse && lastClickWasAnchorUse) return true;
        if (stack.isIn(ItemTags.SWORDS)) return !HabibConfig.swingOnlyOnHit || lastAttackHit;
        return lastClickWasUse && stack.isOf(Items.ENDER_PEARL);
    }

    /** 0 -> 1 -> 0 over the swing, eased so it starts and ends gently. */
    public static float ease(float p) {
        float s = MathHelper.sin(p * (float) Math.PI);
        return s * s * (3f - 2f * s);
    }

    /** Called from the mixin. Returns true if the vanilla swing offset should be skipped. */
    public static boolean apply(MatrixStack m, Arm arm, float p) {
        if (!BetaAccess.isAuthorized()) return false;
        boolean anchorUse = HabibConfig.anchorOptimizer && lastClickWasUse && lastClickWasAnchorUse;
        int style = anchorUse ? 1 : HabibConfig.swingStyle;
        float intensity = anchorUse ? 1f : HabibConfig.swingIntensity;
        if (style <= 0 || style >= COUNT || p <= 0f) return false;
        if (!animatedSwing(arm)) return false;

        // Vanilla adds its own thrust just before calling the swing offset; undo it exactly.
        int i = arm == Arm.RIGHT ? 1 : -1;
        float r = MathHelper.sqrt(p);
        float l = -0.4f * MathHelper.sin(r * (float) Math.PI);
        float mv = 0.2f * MathHelper.sin(r * (float) (Math.PI * 2));
        float f = -0.2f * MathHelper.sin(p * (float) Math.PI);
        m.translate(-i * l, -mv, -f);

        if (style == FLAT_STAB) {
            flatStab(m, i, p, intensity);
            return true;
        }

        float s = ease(p) * intensity;
        float[] st = STYLE[style];
        m.translate(0f, st[0] * s, st[1] * s);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(st[2] * s));
        return true;
    }

    private static final int FLAT_STAB = 4;

    private static float bump(float p, float centre, float width) {
        float d = (p - centre) / width;
        return (float) Math.exp(-d * d);
    }

    private static float smooth(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Fades the motion in and out so it starts and ends exactly at the resting pose. */
    private static float window(float p) {
        return smooth(0f, 0.08f, p) * (1f - smooth(0.88f, 1f, p));
    }

    private static float lift(float p) {
        return bump(p, 0.28f, 0.17f) * window(p);
    }

    private static float sweep(float p) {
        return bump(p, 0.68f, 0.20f) * window(p);
    }

    /**
     * Two-phase swing for a low, flat-held sword: the blade first lifts toward the centre of the
     * screen and points forward (a stab), then sweeps out to the side and drops back to rest.
     */
    private static void flatStab(MatrixStack m, int i, float p, float intensity) {
        float a = lift(p) * intensity;
        float b = sweep(p) * intensity;
        // phase 1: toward centre, up and forward, blade pitched away from the camera
        m.translate(-i * 0.22f * a, 0.10f * a, -0.24f * a);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-58f * a));
        // phase 2: sweep out and down, rolling the blade
        m.translate(i * 0.26f * b, -0.14f * b, 0f);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * -24f * b));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * -32f * b));
    }

    /** Motion amount over time (0..~1.3), only used to draw the preview graph in the menu. */
    public static float curve(int style, float intensity, float p) {
        if (style <= 0) return MathHelper.sin(MathHelper.sqrt(p) * (float) Math.PI);
        if (style == FLAT_STAB) return Math.max(lift(p), sweep(p)) * intensity;
        return ease(p) * intensity * (Math.abs(STYLE[style][0]) / 0.30f);
    }
}
