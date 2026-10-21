# Liquid Glass UI

The 1.21.11 build uses Yarn/Java 21; the 26.2 build uses Mojang names/Java 25. The shared source set contains appearance data, palette interpolation and OS detection. Version-specific GlassRenderer and MenuBlurMixin adapters use each version's GUI and vanilla blur APIs. No new runtime library or custom framebuffer is required.

## Verification

Run ./gradlew build at the repository root with Java 21, and in versions/mc26.2 with Java 25. Each build runs verifyAppearance, a dependency-free assertion runner covering older-config defaults, JSON persistence, invalid values, palette endpoints, alpha composition, readable accent text and system detection or fallback.

An isolated Minecraft 1.21.11 development launch exercised scaled sidebar navigation, Light/Dark segments, HUD position field focus and updates, slider dragging, content scrolling, armor dropdown selection and the reset modal. A duplicate blur request found during that launch was corrected by using Screen's background hook exactly once. Temporary preview instrumentation is not part of the distributable.

Windows application-theme detection was exercised locally. macOS and Linux detection have not been tested on those operating systems. Shader-pack combinations, multiplayer interactions, ultrawide displays and comparative FPS profiling still need user testing. The 26.2 build is compile/check verified; its graphical testing status is recorded in the PR.

## Review checklist

- Open Right Shift at several GUI scales, including a small window.
- Compare Light and Dark, then choose Auto and change the desktop application appearance.
- Exercise module switches, scale and colour, animation settings, packs, optimizers, effects, presets and profile sharing.
- In HUD Editor, change coordinates, scale and armor dropdowns; open the full-screen editor and drag, resize and recolour a widget.
- Toggle blur, reduced transparency and animations, and verify the preferences after restart and profile loading.
- Merge the feature PR only after review. The existing main-branch workflow then builds and publishes 1.15.0 for both Minecraft versions; installed clients receive it through the existing updater after the release succeeds.

## Development screenshots

These are actual Minecraft 1.21.11 development screenshots at 854 x 480, with the menu scaled to fit.

![Light appearance](screenshots/liquid-glass-light.png)

![Dark appearance](screenshots/liquid-glass-dark.png)

![HUD editor](screenshots/liquid-glass-hud.png)
