# Wyrm for Android

Wyrm is a social platform and a snake arena game in one Android app, by
OM Rajput. A native C engine renders the arena with Vulkan; Jetpack Compose
draws everything around it.

This repository holds the app's source and its update channel. The app checks
`update/latest.json` on launch and verifies its signature before trusting a
single field in it. Each APK is attached to the matching release tag.

## Features

- Username/password accounts, profiles, follows, direct messages, global chat,
  leaderboards and notifications through the Wyrm backend.
- Online slither-protocol arenas (protocol 19) with a live arena directory,
  arena codes, recent and saved custom IPv4 arenas. Latency probes run only
  while the arena picker is open, and each arena is dialled at most once a
  minute.
- One arena connection per Play. A refused or failed entry returns to the
  lobby with no automatic retry, and Play can never stay stuck on "Entering".
- Offline Play with AI mode with local bots.
- Skin Studio: presets, pattern builder with the slither.io Android colour
  wheel, accessories, NTL tags and arena backgrounds.
- NTL-compatible Team mode (presence, roster, chat, tags).
- Joystick and arrow controls, on-screen buttons, a draggable arena HUD
  layout editor, themes, food styles and bot mode.
- Voice rooms, `.wyrm` backup and restore, and a signed in-app updater.
- iOS-style Liquid Glass interface (floating tab bar, glass controls).

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
touches engine state directly: requests go through mutex-guarded mailboxes
drained on the engine thread.

## Repository layout

| Path | Contents |
|---|---|
| `app/src/` | C engine: gameplay, slither protocol, renderer glue, JNI bridges |
| `app/res/` | Fonts, textures and shader sources packaged as assets |
| `android/` | Gradle project; Kotlin/Java under `android/app/src/main/java/com/wyrm/omrajput/` |
| `android-sdl/` | Vendored SDL3 |
| `thermite/` | Vulkan rendering framework |
| `glfw/` | Desktop window/input dependency kept for upstream compatibility |
| `tools/`, `scripts/`, `tests/` | Asset tools, build script, protocol tests |
| `update/` | Signed update manifest read by installed apps |

## Building

Requirements: JDK 17 (Android Studio JBR), Android SDK 36, NDK
`28.2.13676358`, CMake `3.22.1`, and an ARM64 Android 8.0+ device with Vulkan.

1. Generate the SPIR-V shaders with `python build.py` (needs `slangc`). The
   compiled `.spv` files are not committed.
2. Add your own Firebase `android/app/google-services.json`. It is not
   published.
3. Build a debug APK:

   ```powershell
   cd android
   .\gradlew.bat :app:assembleDebug -PWYRM_VERSION_CODE=151 -PWYRM_VERSION_NAME=1.5.1
   ```

Optional Gradle properties: `WYRM_API_URL`, `GOOGLE_WEB_CLIENT_ID`,
`WYRM_VERSION_CODE`, `WYRM_VERSION_NAME`.

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
