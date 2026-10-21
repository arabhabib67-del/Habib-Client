package dev.habib.client.gui.theme;

/** Monotonic exponential damping: interruptible and independent of the frame rate. */
public final class AnimationUtils {
    private AnimationUtils() {}
    // A quick pop, a small overshoot, then settle.
    public static float openingSpring(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0f) return 0f;
        if (seconds >= 0.8f) return 1f;
        double damping = 0.58;
        double omega = 20.0;
        double damped = omega * Math.sqrt(1.0 - damping * damping);
        return (float) (1.0 - Math.exp(-damping * omega * seconds)
                * (Math.cos(damped * seconds) + damping * omega / damped * Math.sin(damped * seconds)));
    }

    public static float follow(float current, float target, float dt, float speed) {
        if (!Float.isFinite(dt) || dt <= 0f) return current;
        float result = current + (target - current) * (float) -Math.expm1(-Math.min(dt, 0.25f) * speed);
        return Math.abs(result - target) < 0.0001f ? target : result;
    }
}

// Copyright (c) 2026 Bloko. Licensed under the MIT License.
