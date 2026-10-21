package dev.habib.client.gui.theme;

/** Marks screens that use Habib's scoped vanilla blur preference. */
public interface GlassScreen {
    default boolean glassBlurAllowed() { return true; }
}

// Copyright (c) 2026 Bloko. UI contributions licensed under the MIT License.
