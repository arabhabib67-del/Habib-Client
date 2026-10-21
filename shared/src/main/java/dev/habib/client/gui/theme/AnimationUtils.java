package dev.habib.client.gui.theme;

/** Monotonic exponential damping: interruptible and independent of the frame rate. */
public final class AnimationUtils {
    private AnimationUtils() {}
    public static float follow(float current, float target, float dt, float speed) {
        if (!Float.isFinite(dt) || dt <= 0f) return current;
        float result = current + (target - current) * (float) -Math.expm1(-Math.min(dt, 0.25f) * speed);
        return Math.abs(result - target) < 0.0001f ? target : result;
    }
}

// Copyright (c) 2026 Bloko. Licensed under the MIT License.
