package dev.habib.client.util;

import fun.crashsystem.jdrpc.DiscordIPC;
import fun.crashsystem.jdrpc.activity.Activity;
import fun.crashsystem.jdrpc.activity.ActivityType;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Publishes a small, static Rich Presence while the Discord desktop app is available. */
public final class DiscordPresence {
    private static final long APPLICATION_ID = 1_562_636_694_363_177_012L;
    private static final String DISCORD_INVITE = "https://discord.gg/ATFhEJPTj";
    private static final long RETRY_SECONDS = 15L;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private static volatile ScheduledExecutorService executor;
    private static volatile DiscordIPC connection;
    private static volatile boolean activitySent;

    private DiscordPresence() {}

    /** Starts a background retry loop; Discord being closed never blocks game startup. */
    public static void start() {
        if (!RUNNING.compareAndSet(false, true)) return;
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "Habib Discord Presence");
            thread.setDaemon(true);
            return thread;
        });
        executor = service;
        service.scheduleWithFixedDelay(DiscordPresence::connectAndPublish,
                0L, RETRY_SECONDS, TimeUnit.SECONDS);
    }

    /** Clears the activity and closes IPC when the game exits. */
    public static void stop() {
        if (!RUNNING.compareAndSet(true, false)) return;
        ScheduledExecutorService service = executor;
        executor = null;
        if (service != null) service.shutdownNow();

        DiscordIPC current = connection;
        connection = null;
        activitySent = false;
        if (current == null) return;
        try {
            if (current.isConnected()) current.clearActivity();
        } catch (Throwable ignored) {
            // Discord may already be shutting down.
        }
        closeConnection(current);
    }

    private static void connectAndPublish() {
        if (!RUNNING.get()) return;
        DiscordIPC current = connection;
        try {
            if (current == null || !current.isConnected()) {
                closeConnection(current);
                current = DiscordIPC.create(APPLICATION_ID);
                connection = current;
                activitySent = false;
                current.connect();
            }
            if (!RUNNING.get() || !current.isConnected() || activitySent) return;

            Activity activity = new Activity.Builder()
                    .setType(ActivityType.PLAYING)
                    .setDetails("Using Habib Client")
                    .setState("In Minecraft")
                    .setLargeImage("habib_logo", "Habib Client")
                    .addButton("Join Discord", DISCORD_INVITE)
                    .build();
            current.setActivity(activity);
            activitySent = true;
        } catch (Exception | LinkageError ignored) {
            DiscordIPC failed = connection;
            connection = null;
            activitySent = false;
            closeConnection(failed);
        }
    }

    private static void closeConnection(DiscordIPC current) {
        if (current == null) return;
        try {
            current.close();
        } catch (Throwable ignored) {
            // A failed IPC connection is retried on the next interval.
        }
    }
}
