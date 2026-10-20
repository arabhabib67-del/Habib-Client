# Habib Client

A PvP-focused client-side Fabric mod for **Minecraft 1.21.11** and **26.2**. Every HUD element is a draggable module. Install the jar matching your Minecraft version; each version uses its own compatible build.

## Modules
| Module | What it shows |
|---|---|
| FPS | Frame rate, colour-coded (green 120+, yellow 60+, red below) |
| Ping | Latency to the server, colour-coded |
| CPS | Left | right clicks per second |
| Keystrokes | WASD, space, LMB/RMB with CPS |
| Armor Status | Armor + held item with remaining durability |
| Potion Status | Active effects, level and time left |
| Target HUD | Name + health bar of the entity you last looked at |
| Combo | Hits landed in a row (resets when you take damage) |
| Reach | Distance of your last hit |
| Item Counter | Totems, end crystals, obsidian, golden apples, ender pearls, XP bottles |
| Health | Your health plus absorption, coloured by how low you are |
| Target Distance | Distance to the player you last hit or looked at |
| Totem Pops | How many totems you and your current target have popped (resets on death) |
| Bow / Crossbow | Bow draw percent and crossbow charge percent, colour-coded red, yellow, then green as it builds; green when full or loaded (only shows while you hold one) |
| Arrows | How many arrows you have (only shows while you hold a bow or crossbow) |
| Hunger | Food level and saturation |
| Toggle Sprint | Off by default. Keeps you sprinting while W is held (Movement tab) |
| Spotify | The now-playing card (Info tab, off by default) shows song, artist, timeline and Spotify album art when the local player provides it. Artwork downloads in the background; no Spotify login or developer token is needed. Playback works in-game with **F7** (previous), **F8** (play/pause) and **F9** (next); rebind them under Options > Controls > Habib Client. Windows uses the system media session, macOS uses AppleScript (allow Minecraft/Java under System Settings > Privacy & Security > Automation), and Linux uses `playerctl`. The card stays visible with a “Nothing playing” message when no track is active, and keeps the last good track briefly through transient player errors. |
| Coordinates / Direction / Speed | Position, facing, blocks per second |
| Movement Status | Sneaking / Sprinting / Walking |
| Clock / Memory | Local time, JVM memory use |
| Zoom | Hold **C** to zoom (FOV scaled by `zoomFactor` in the config) |

The default layout is three tidy columns (info on the left, combat in the middle, gear on the right). Only the essentials start switched on (FPS, ping, CPS, combo, reach, health, keystrokes, armor, item counter, target, potions, bow timer, arrows); everything else is one click away in the menu, and **Reset layout** returns to this setup.

All features are display-only or visual, so they stay within the rules of normal servers. There are no cheats.

## Menu
Press **Right Shift** in a world to open the Habib Client menu.

- **Info / Combat / Movement**: every HUD module sorted into categories. Click a row (or its switch) to turn it on or off, use `-` / `+` to resize it and the colour square to change its colour. Scroll to move through long lists.
- **Animations**: pick one animation style for sword hits and ender pearl throws (Vanilla, Smooth Dip, Soft Tap, Low Chop, Flat Stab) and set its intensity. It plays when your sword hits an entity (swinging at air or blocks stays vanilla; the **Full animation only on hit** switch controls this) or when you right-click to throw an ender pearl; every other item keeps the normal vanilla animation. Smooth Dip makes your item come down and back up instead of swinging outward. The graph shows the motion.
- **Packs**: search Modrinth for resource packs that match your Minecraft version and install them without leaving the game. Click **Install** and the pack is downloaded to `resourcepacks/`, verified against Modrinth's SHA-1 and enabled straight away (resources reload automatically). The **Installed** view lists every pack in your resourcepacks folder with a switch to turn each on or off, and its search box filters them by name as you type. The **Enabled** view shows only your packs from the resourcepacks folder that are currently active (highest priority first) so you can switch any of them off.
- **Optimize**: **Crystal optimizer** removes an end crystal from your screen the moment you hit it, instead of waiting for the server (the hit is still sent normally; it skips crystals out of reach and when weakness would stop the hit from breaking it). **No explosion particles**, **No totem particles** and **No hit particles** hide purely visual effects to cut crystal-fight lag (the totem pop counter still works). **Ender pearl optimizer** hides the purple portal particles a thrown pearl leaves behind and the burst when it lands or you teleport (only particles near a pearl or a teleport, so nether portals and endermen look the same); pearls still fly, land and teleport exactly as normal. **Consumable optimizer** hides the crumbs and sips from eating and drinking and the burst from splash and lingering potions (only particles near someone eating or drinking, or near a potion in the air); eating, drinking and potion effects work exactly as normal. All switches start off.
- **Armor HUD options** (Layout tab): show the armor as a vertical list or a horizontal row, show or hide the item in your hand, and show durability as uses left, percent, the **vanilla durability bar** on the item (the same green-to-red bar you see in your inventory) or not at all. Empty slots are skipped so there are never gaps.
- **Effects**: optional screen alerts for crystal fights, all off by default. **Low health warning** pulses the screen edge red at 3 hearts or less, **Totem pop flash** flashes gold when you pop and green when your target pops, **Totem pop messages** adds a chat line for those pops, and **Low armor warning** pulses orange when a piece is under 10% durability.
- **Presets**: one-click graphics presets. **Minimal** (6 chunks, minimal particles, no shadows, unlimited FPS), **Medium** (10 chunks, fewer particles, shadows on, unlimited FPS) and **High** (12 chunks, all particles, shadows on, unlimited FPS). All three turn VSync off and set the FPS cap to unlimited. The active preset is highlighted, or it shows "Custom" if you've changed something. Clouds and graphics mode are left alone.
- **Layout**: open the layout editor to drag modules around, scroll to resize, right-click to recolour. Esc goes back. The **Box behind HUD** switch turns the translucent box off so modules show as plain text only (held keystrokes turn yellow instead of lighting up their box).
- **Profiles**: save your whole setup under a name (HUD modules, positions, sizes, colours, optimizers, effects, animation, zoom and armor settings). **Save new** stores your current setup, **Load** switches to a profile, **Update** overwrites it with your current settings, **Share** copies a code like `HC1-...` to your clipboard, and **x** deletes it (click twice). **Paste code** adds a profile from a code you copied; press Load to use it. Profiles never include the auto-update settings.
- **Menu navigation**: Up/Down arrows or the number keys (1-9, 0) switch tabs, PageUp/PageDown/Home/End move through long lists, and the scroll bar can be dragged. Each tab remembers its scroll position, and clicking outside the panel closes the menu.
- **C** (hold): zoom
- **F7 / F8 / F9**: Spotify previous / play or pause / next
- Keys can be rebound under Options > Controls > Habib Client

Settings are saved to `config/habibclient.json`.

## Beta access

All beta testers use one shared password. Enter it once when the game opens; it unlocks the modules until the game closes.

Before building or publishing beta jars, add a GitHub Actions repository secret named `BETA_ACCESS_PASSWORD`:

1. Open the Habib Client repository on GitHub and choose **Settings**.
2. Open **Secrets and variables** → **Actions**.
3. Choose **New repository secret**, enter `BETA_ACCESS_PASSWORD` as the name, and enter the shared tester password as the value.
4. Save it. For this 1.14.8 update, open the failed run under **Actions** and choose **Re-run jobs**. Later updates build automatically when you push a version change. Give testers the same password you saved as the secret.

Do not put the password in a source file, Gradle properties file, commit, or issue. This is a simple beta gate: every tester shares the same password, so they can pass it on, and someone with a jar could attempt to guess it offline. Use a unique password that you do not use anywhere else.

## Auto-update
The mod can update itself from your own GitHub repo, so you never have to send or copy a jar by hand again.

**One-time setup**
1. Create a **public** repo on GitHub (for example `arabhabib67-del/Habib-Client`) and upload the contents of this folder to it (the folder that holds `build.gradle` becomes the repo root), on the `main` branch.
2. In `gradle.properties` set `update_repo=arabhabib67-del/Habib-Client` (already set), then commit that change.
3. Keep `mod_version` in both `gradle.properties` files in sync. Open the repo's **Actions** tab. The build runs by itself and creates a **Release** named `v<mod_version>` with separate jars for Minecraft 1.21.11 and 26.2 attached.
4. Download that jar once, put it in your `mods` folder, and start the game.

**Every update after that**
Raise `mod_version` in both Gradle property files, commit and push. The workflow publishes a new release. Next time you launch the game the mod checks for it and shows a top-right toast when a newer version is available. With auto-update on, it downloads the update, verifies it, then shows a restart toast when the update is ready to install after you close the game. A new version only counts if its number is higher than the one you have.

**In the game**
The menu header shows your version and update status (for example `v1.11.0 - Up to date`). Click it to check right now, or to install when an update is waiting. Update notices appear as Minecraft-style toasts in the top-right corner.

**Settings** (`config/habibclient.json`): `autoUpdate` (true/false) checks on launch; true also downloads automatically, while false only notifies you and waits for you to click the version in the menu header. `updateRepo` overrides the repo built into the jar. With no repo set the updater stays off.

**Safety**: the file must come from `github.com/<your repo>/releases/download`, must match the SHA-256 checksum GitHub lists for it, and must be this mod (id `habibclient`) at the announced version, or it is thrown away. It is only installed after the game has closed, the old jar is kept aside until the new one is in place, and nothing happens in a development build. Keep 2-step login on the GitHub account that owns the repo: whoever can publish a release there can change the mod on your computer.

If the swap ever fails the new jar stays in `config/habibclient-update/` with a `last-update.log` explaining why; copy it into `mods/` and delete the old jar yourself.

## Minecraft versions

Each Minecraft version has a separate jar because 26.2 uses a different Java version and Minecraft naming system from 1.21.11.

| Minecraft | Java | Build folder | Release jar suffix |
|---|---:|---|---|
| 1.21.11 | 21 | repository root | `mc1.21.11.jar` |
| 26.2 | 25 | `versions/mc26.2` | `mc26.2.jar` |

Install only the jar whose suffix matches your Minecraft version. The in-game updater also selects and verifies the matching jar automatically.

## Build

Both builds need internet access to fetch Minecraft and Fabric dependencies. The 1.21.11 build requires JDK 21; the 26.2 build requires JDK 25.

```
./gradlew remapJar
```

For Minecraft 26.2, run from `versions/mc26.2`:

```
./gradlew jar
```

Each jar is written to that build's `build/libs/` folder. Put the matching jar in your `mods` folder with Fabric Loader 0.19.5+ and the matching Fabric API release.

To run the 1.21.11 build straight from the project: `./gradlew runClient`.
