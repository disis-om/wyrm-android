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
| Beta | `update/beta.json` (+ `.sig`) | players who turn on **Settings › Backup › Beta updates**; they are offered whichever of the two is newer |

Every release is a tagged GitHub release with the APK attached. Betas step the
patch number (7.1.1, 7.1.2…) and say "(beta)" in their title. Released: **7.1.0 (710)** stable (2026-10-09).
Current source/test version: **7.1.5 (715)**, not released. It includes Skin
Studio and saved skins, NTL-compatible bot, background play, joystick knob modes,
phone Back routing, larger on-screen keys, flying tags and message deletion.
Build and device verification are recorded separately in `AGENTS.md`.

## Features

- **Accounts and social:** username/password accounts, profiles, follows,
  direct messages, global chat, leaderboards, notifications and voice rooms,
  all through the Wyrm backend.
- **Delete messages:** touch and hold your own Global or direct message, then
  Delete; the active server row is removed and connected clients drop it live.
- **Observatory:** the separate hosted operator dashboard includes a Live
  presence page that can be opened with its own read-only password.
- **Online arenas:** slither protocol 19, a live arena directory, arena codes,
  and recent and saved custom arenas.
  - Latency probes run only while the arena picker is open, and each arena is
    dialled at most once a minute.
  - One arena connection per Play. A refused entry returns to the lobby with no
    automatic retry.
- **Offline play:** Play with AI, with local bots. The assist bot follows NTL's
  grow/circle behavior and keeps Wyrm's border guard.
- **Skin Studio:** presets, a pattern builder with the slither.io colour wheel,
  Wyrm beads, accessories, Wyrm looks (hair, ears, glasses) and arena
  backgrounds. A custom skin is sent to the arena in at most 146 colour runs
  (300 bytes), the same limit NTL uses; longer skins got the snake refused.
  The new Studio paints local beads and eyes; the classic editor also keeps
  saved skins, and tags fly away when a snake dies.
- **Trails:** photo and text posts with a story-style studio. After a run,
  "Share run" opens it with the run's screenshot or your skin, a stats box
  (score, kills, time) and text. A post can share its skin, and other players
  can try it on ("Try this skin") before wearing it.
  Deleting a Trail removes its rows and unreferenced photos/thumbnails/videos
  from active storage; backups retain older copies until their retention ends.
- **Help & feedback:** report a problem, suggest an idea or ask for help,
  with optional device info and a recent log; crash and arena-drop reports
  (always asked first unless you turn on automatic sending) and replies from
  Wyrm as notifications.
- **Controls:** joystick or arrow steering, with 5 drawn arrows and 20 image
  arrows, and a picker with a live preview. Also on-screen buttons and a
  draggable arena HUD layout editor, joystick knob modes and keys up to 2.5x.
- **Background play:** the game/socket/bot can keep running without GPU frames
  while a foreground service shows a notification and keeps CPU/Wi-Fi awake.
- **Team mode:** NTL-compatible presence, roster (FPS, ping, leaderboard place)
  and chat, with several saved teams.
- **Look and account settings:** themes, food styles, bot mode and layouts
  save to the account and restore on login; shared play options follow Android
  and iPhone. Logging out clears the device. Updates use a signed manifest.

NTL services are on (since 2026-10-04). Do not turn them off without a yes.

## Architecture

```text
Jetpack Compose interface (Kotlin)       Android activity, updater, backup (Java)
                 │                                   │
                 └──────── JNI mailboxes and callbacks ────────┘
                                   │
                    C engine: game, protocol, bot, UI overlay
                                   │
                  SDL3 (window, input) · Thermite · Vulkan
```

The app is portrait; the lobby and the arena are landscape. Compose never
touches engine state directly: requests go through mutex-guarded mailboxes that
are drained on the engine thread.

## Repository layout

| Path | Contents |
|---|---|
| `app/src/` | C engine: gameplay, slither protocol, renderer glue, touch controls, JNI bridges (`platform/`) |
| `app/res/` | Fonts, textures (including the arrow atlas) and shader sources, packaged as assets |
| `android/` | The Gradle project; Kotlin/Java under `android/app/src/main/java/com/wyrm/omrajput/` |
| `android-sdl/` | Vendored SDL3 |
| `thermite/` | The Vulkan rendering framework |
| `glfw/` | The desktop window/input dependency, kept for upstream compatibility |
| `tools/`, `scripts/`, `tests/` | Asset tools, the build script, the release script and protocol tests |
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
   .\gradlew.bat :app:assembleDebug "-PWYRM_VERSION_CODE=628" "-PWYRM_VERSION_NAME=6.2.8"
   ```

Optional Gradle properties: `WYRM_API_URL`, `GOOGLE_WEB_CLIENT_ID`,
`WYRM_VERSION_CODE`, `WYRM_VERSION_NAME`. The version reaches the C engine
through a generated header (`android/app/.cxx-version/wyrm_version.h`), so a
new version does not rebuild the whole engine.

The first native build compiles the whole engine and takes several minutes.

Release signing reads `release-signing/keystore.properties`, which is private
and not in this repository. Debug builds cannot replace official builds.

## Security

Report security issues privately to OM Rajput through the contact methods at omrajput.in; do not open a public issue with credentials, exploit details or private data.

No signing keys, keystores, passwords, service credentials or player data are
stored here. The only key file is the updater's public verification key. Team
credentials and sessions live in the Android Keystore on the device.

## License

GNU GPL v3; see `LICENSE` and `NOTICE`. Third-party components keep their own
licenses in their folders. Slither.io names, artwork and trademarks belong to
their owners. Wyrm is independent and not affiliated with the game's developer.
