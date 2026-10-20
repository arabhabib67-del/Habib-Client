package dev.habib.client.util;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;

/** Counts mouse clicks in a rolling one second window by polling the mouse once per frame. */
public final class CpsTracker {
    private static final Deque<Long> LEFT = new ArrayDeque<>();
    private static final Deque<Long> RIGHT = new ArrayDeque<>();
    private static boolean leftDown;
    private static boolean rightDown;

    private CpsTracker() {}

    /** Call once per rendered frame. */
    public static void poll(Minecraft mc) {
        long window = mc.getWindow().handle();
        boolean l = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        long now = System.currentTimeMillis();
        if (mc.gui.screen() == null) {
            if (l && !leftDown) LEFT.addLast(now);
            if (r && !rightDown) RIGHT.addLast(now);
        }
        leftDown = l;
        rightDown = r;
    }

    public static int left() {
        return count(LEFT);
    }

    public static int right() {
        return count(RIGHT);
    }

    private static int count(Deque<Long> q) {
        long cutoff = System.currentTimeMillis() - 1000L;
        while (!q.isEmpty() && q.peekFirst() < cutoff) {
            q.removeFirst();
        }
        return q.size();
    }
}
