# Habib Client changelog

Version numbers for 1.1.0 and up were assigned afterwards to group the changes in the order they were made.
## 1.14.1 - Spotify artwork and playback controls
- Added the Spotify cover image to the now-playing HUD when the desktop player exposes artwork. It loads in the background and keeps a colored fallback tile if artwork is unavailable.
- Added configurable F7 previous, F8 play/pause, and F9 next shortcuts. Commands run outside Minecraft's game thread.
- Retains the last good track during brief media-session errors and retries artwork downloads after a temporary failure.
- Updated the separate 26.2 build for its changed HUD, screen, toast and item-rendering APIs.

## 1.14.3 - Spotify HUD stays visible
- Keeps the Spotify card visible during gameplay when no track metadata is available, showing a “Nothing playing” placeholder while keeping its in-game controls available.

## 1.14.2 - HUD layout center guides
- Added a labeled screen-center crosshair to the HUD layout editor so it is easy to see when an element is centered.
- HUD elements now snap to the screen's horizontal and vertical center when dragged within 8 pixels of either guide.

## 1.14.0 - Minecraft 26.2 support
- Added a separate Minecraft 26.2 build while keeping the existing Minecraft 1.21.11 build.
- Release automation builds and attaches one jar per supported Minecraft version; the in-game updater chooses and verifies the jar matching the installed version.
- The 26.2 port uses Mojang's unobfuscated names and Java 25. The 1.21.11 build stays on Yarn mappings and Java 21.

## 1.13.0 - Profiles and share codes
- New **Profiles** tab (last in the sidebar). Type a name and press **Save new** to store your current setup: every HUD module's on/off, position, size and colour, plus the optimizers, effects, animation, zoom and armor settings.
- **Load** switches to a profile, **Update** overwrites it with your current settings, **Share** copies a share code to the clipboard, and **x** deletes (click twice to confirm). Up to 50 profiles, saved in `config/habibclient-profiles.json`.
- **Paste code** reads a share code from the clipboard and adds it to your list. It does not change your settings until you press Load.
- Share codes are one line starting with `HC1-`. Imported codes are size-limited and every value is range-checked. Profiles never contain the auto-update settings, so a code can't change where the mod updates from.
- The sidebar has 11 tabs now, so tab spacing is tighter. Profiles has no number key (Up and Down reach it).

## 1.12.0 - Consumable optimizer
- New **Consumable optimizer** switch in the Optimize tab (off by default). It hides the food crumbs and sip particles from anyone eating or drinking, and the burst from splash and lingering potions.
- Visual only: eating, drinking and potion effects are unchanged. Only particles near someone eating or drinking, or near a potion in the air, are hidden. Potion zones last 1 second so bursts that arrive late on high ping are still hidden.
- Lingering potion clouds and effect swirls around players are left alone on purpose.
- The Optimize tab rows are spaced a little tighter to fit the sixth switch.

## 1.11.0 - Auto-updater
- The mod checks a GitHub repo for a newer release on launch, downloads it and installs it after the game closes. Version and status show in the menu header (click to check or install).
- Safety checks: download must come from the repo's release URL, match GitHub's SHA-256 for the file, and be this mod at the announced version. Old jar is kept aside until the new one is in place; development builds never update.
- New settings `autoUpdate` and `updateRepo` in `config/habibclient.json`; the repo can also be built into the jar with `update_repo` in `gradle.properties`.
- Build workflow now publishes a GitHub Release (with the jar) for each new `mod_version`.
- Needs a public GitHub repo; see README > Auto-update for the one-time setup.

## 1.10.0 - Spotify card
- The Spotify module is now a now-playing card: song title, artist, `elapsed / total` time and a progress bar along the bottom (red by default, recolourable with the module colour, grey when paused).
- Now reads position and length: Windows system media controls (title-only fallback if unavailable), macOS AppleScript, Linux playerctl. Progress is worked out locally between checks so the bar moves smoothly.
- Playback controls and platform album art were added in 1.14.1. The Windows title fallback still has no timeline.

## 1.9.1 - Pearl optimizer reliability
- Pearl optimizer keeps its hide-zones for 1 second and a 5 block radius so landing / teleport particles are still hidden on high-ping connections, where they arrive after the pearl is gone. Zone list is also de-duplicated to keep the check cheap.
- Pearl flight speed itself is server-controlled and is not changed.

## 1.9.0 - Ender pearl optimizer, schematic removed
- New **Ender pearl optimizer** switch in the Optimize tab (off by default): hides the portal particles from thrown pearls and their landing / teleport burst. Visual only; nothing about how pearls work changes. Other portal particles are untouched.
- Removed the built-in **Schematic** tab (the Litematica stand-in added in 1.7.0 and extended in 1.7.1) along with its file readers. The sidebar is back to 10 tabs.

## 1.8.1 - macOS Spotify polish
- macOS: shows "Allow Automation for Spotify" if the permission prompt was declined, and a pending prompt can no longer stall the poller.

## 1.8.0 - Spotify module
- New **Spotify** module (Info tab, off by default) showing `Artist - Song` from the Spotify desktop app.
- Uses local info only: Windows window title, macOS AppleScript, Linux `playerctl`. No account, token or network. Only polls while the module is enabled.
- Limits: shows nothing if Spotify is closed or in the tray (Windows), Spotify web player isn't detected, no playback controls.

## 1.7.1 - Schematic fixes
- Schematic tab now also loads `.schem` (Sponge/WorldEdit v1-v3) and vanilla structure `.nbt` files, not just `.litematic`.
- Files view shows the exact schematics folder path when it is empty.
- The in-world overlay explains itself when nothing is on screen (for example "Schematic is 240 blocks away"). Marker range raised to 16 blocks.

## 1.7.0 - Built-in schematic projection
- New **Schematic** tab (Files / Setup / Materials / Layers) that loads `.litematic` files from the `schematics` folder without needing Litematica.
- On-screen markers for missing (green) and wrong (red) blocks within 12 blocks, "missing only" or "all blocks" mode, single-layer filter, line-of-sight only.
- Placement with Here + X/Y/Z nudge buttons, progress counter, material list (need / left / have), top-down layer viewer.
- Own NBT reader (no Minecraft NBT classes). Sidebar re-spaced again for 11 tabs.
- Limits: 500,000 blocks max, no rotate/mirror, flat markers instead of ghost block models, no auto-placing.

## 1.6.0 - Smooth navigation
- Up/Down arrows and number keys (1-9, 0) switch tabs.
- PageUp / PageDown / Home / End move through long lists; one wheel notch scrolls exactly one row.
- Draggable scroll bar (turns orange while dragging).
- Each tab remembers its scroll position.
- Clicking outside the panel closes the menu.
- Hover highlights fade smoothly on every row; sidebar tabs get a soft hover highlight; faint shortcut numbers on client tabs.
- Keyboard shortcuts are ignored while typing in the Packs search box and in the layout editor.

## 1.5.0 - Effects
- New **Effects** tab, all options off by default:
  - Low health warning (pulsing red screen edge at 3 hearts or less)
  - Totem pop flash (gold when you pop, green when your target pops)
  - Totem pop messages (chat line for you / your target)
  - Low armor warning (orange pulse when a piece is under 10% durability)
- New `Effects.java`, config fields, README entry. Sidebar spacing tightened to fit the extra tab.

## 1.4.0 - Cleaner, flatter UI
- Module rows are a flat list with hairline separators; only the hovered row gets a plate.
- Size controls (- / 1.0x / +) appear on hover only; colour swatch always visible and dimmed when the module is off.
- Solid accent switches with a white knob; orange reserved for the active tab, switches that are on and primary buttons.
- Removed corner brackets, top accent line, logo underline and orange count badges.
- Bold plain section headings, cooler neutral palette, scroll bar indicator on long lists.

## 1.3.0 - Categories and more modules
- Four module categories: **Info, Combat, Gear, Movement** (new Gear tab; sidebar re-spaced).
  - Info: FPS, Ping, Coordinates, Direction, Clock, Memory, Session Timer, Date
  - Combat: CPS, Combo, Reach, Health, Target HUD, Target Distance, Totem Pops, Hit Counter, Attack Cooldown
  - Gear: Armor Status, Potion Status, Item Counter, Hunger, Bow / Crossbow, Arrows, Held Durability, Totem Count
  - Movement: Keystrokes, Speed, Movement Status, Toggle Sprint, Yaw / Pitch, Distance Travelled
- 8 new modules (all off by default): Session Timer, Date, Hit Counter, Attack Cooldown, Held Durability, Totem Count, Yaw / Pitch, Distance Travelled.

## 1.2.0 - UI refresh
- Accent status stripes on rows, active-tab plate and marker, enabled-count badges on module tabs.
- Section heading style, breadcrumb in the header, soft drop shadow, corner brackets (removed again in 1.4.0).

## 1.1.0 - Bow draw by colour
- Bow / Crossbow HUD is now a colour-coded bar (red, yellow, green) instead of a percentage; off by default from 1.1.0 onward.
- The bow and crossbow items themselves tint red to yellow to green while drawn (hand, hotbar and third person) via `assets/minecraft/items/bow.json` and `crossbow.json`; a loaded crossbow stays green.

## 1.0.2 - Original upload
- FPS, ping, CPS, keystrokes, armor, potions, target HUD, combo, reach, item counter, health, distance, totem pops, arrows, hunger, toggle sprint, coordinates, direction, speed, status, clock, memory.
- Swing animations, resource pack browser (Modrinth), optimizers, graphics presets, layout editor, zoom.
