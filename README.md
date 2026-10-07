# Wyrm for Android

Wyrm is a social platform and a snake arena game in one Android app, by
**OM Rajput**. A native C engine draws the arena with Vulkan; Jetpack Compose
draws everything around it, in an iOS-style Liquid Glass interface.

This repository holds the app's source, its releases and its update channel.

## Updates and releases

Installed apps read a signed manifest from this repository and verify its
signature before they trust a single field in it.

| Channel | Manifest | Who is offered it |
|---|---|---|
| Stable | `update/latest.json` (+ `.sig`) | everyone |
| Beta | `update/beta.json` (+ `.sig`) | players who turn on **Settings › Updates › Beta updates**; they are offered whichever of the two is newer |

Every release is a tagged release with the APK attached. A stable release
moves the major or minor number; betas step the patch number and say "(beta)"
in their title. The current stable release is **7.0.0**.

## Features

- **Account:** username/password accounts. Every setting lives in the
  account, one copy per platform plus a shared copy (skin, look, background,
  name and play options follow you between Android and iPhone). Settings are
  saved on log out and restored during the log-in animation.
- **Social:** profiles, follows, direct messages, global chat, leaderboards
  with search, notifications, voice rooms and **Trails** (photo, text and
  canvas posts with a story-style editor, looks, stickers and replies).
- **Online arenas:** slither protocol 19, a live arena directory with a
  lowest-ping pick, arena codes, and recent and saved custom arenas.
  - Latency probes run only while the arena picker is open, and each arena is
    dialled at most once a minute.
  - One arena connection per Play. A refused entry returns to the lobby with no
    automatic retry.
- **Offline play:** Play with AI, with local bots.
- **Skin Studio:** 66 presets, a pattern builder with the slither.io colour
  wheel and Wyrm's own beads, accessories, Wyrm looks (hair, ears, glasses),
  30 arena floors and 241 tags. Tags show to everyone in the arena: only the
  tag's number travels in the skin block, and each player draws the chain,
  swing and size with their own settings.
- **Modes:** Wyrm, assist and Near Original (slither's own HUD and
  controls); Texture, Solid, Flat and Skinless snake rendering, and Spine.
- **Controls:** joystick or arrow steering, drawn and image arrows, look
  ahead, a spring zoom bar, on-screen buttons (including Auto restart and Eyes
  back) and a draggable arena HUD layout editor; landscape or portrait play.
- **Team mode:** NTL-compatible presence, an in-arena roster and team chat
  window, with several saved teams.
- **Performance:** Auto, Balanced and Performance modes with an FPS limit and
  a thermal step-down; the engine draws only when it is on screen.
- **Help & feedback:** crash and arena-drop reports (sent only with your
  consent), reports to Wyrm with replies, a FAQ and an app tour.
- **Updates:** a signed in-app updater with stable and beta channels.

## Architecture

```text
Jetpack Compose interface (Kotlin)       Android activity, updater (Java)
                 │                                   │
                 └──────── JNI mailboxes and callbacks ────────┘
                                   │
                    C engine: game, protocol, bot, UI overlay
                                   │
                  SDL3 (window, input) · Thermite · Vulkan
```

Compose never touches engine state directly: requests go through
mutex-guarded mailboxes that are drained on the engine thread.

## Repository layout

| Path | Contents |
|---|---|
| `app/src/` | C engine: gameplay, slither protocol, renderer glue, touch controls, JNI bridges (`platform/`) |
| `app/res/` | Fonts, textures (bead, tag and arrow atlases, floors) and shader sources, packaged as assets |
| `android/` | The Gradle project; Kotlin/Java under `android/app/src/main/java/com/wyrm/omrajput/` |
| `android-sdl/` | Vendored SDL3 |
| `thermite/` | The Vulkan rendering framework |
| `glfw/` | The desktop window/input dependency, kept for upstream compatibility |
| `tools/` | Asset tools (floors, textures, the tag sheet builder in `wyrm-tags/`), the release script and checks |
| `scripts/`, `tests/` | The build script and protocol tests |
| `update/` | The signed update manifests read by installed apps |

## Building

Requirements:
- JDK 17 (the Android Studio JBR)
- Android SDK 36
- NDK `28.2.13676358`
- CMake `3.22.1`
- an ARM64 Android 8.0+ device with Vulkan

1. Generate the SPIR-V shaders with `python build.py` (it needs `slangc`). The
   compiled `.spv` files are not committed.
2. Add your own Firebase `android/app/google-services.json`. It is not published.
3. Build:

   ```powershell
   cd android
   .\gradlew.bat :app:assembleDebug "-PWYRM_VERSION_CODE=702" "-PWYRM_VERSION_NAME=7.0.2"
   ```

Optional Gradle properties: `WYRM_API_URL`, `GOOGLE_WEB_CLIENT_ID`,
`WYRM_VERSION_CODE`, `WYRM_VERSION_NAME`. The version reaches the C engine
through a generated header (`android/app/.cxx-version/wyrm_version.h`), so a
new version does not rebuild the whole engine.

The first native build compiles the whole engine and takes several minutes.

Release signing reads `release-signing/keystore.properties`, which is private
and not in this repository. Debug builds cannot replace official builds.

## Security

No signing keys, keystores, passwords, service credentials or player data are
stored here. The only key file is the updater's public verification key. Team
credentials and sessions live in the Android Keystore on the device.

## License

GNU GPL v3; see `LICENSE` and `NOTICE`. Third-party components keep their own
licenses in their folders. Slither.io names, artwork and trademarks belong to
their owners. Wyrm is independent and not affiliated with the game's developer.
