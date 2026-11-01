# Cheat support (Game Boy Advance)

This fork adds a small, native cheat manager to Lemuroid. It reuses the libretro cheat API that the
emulator cores already implement (`retro_cheat_set` / `retro_cheat_reset`, exposed by LibretroDroid),
keeps everything local, and leaves the rest of Lemuroid untouched.

Phase 1 targets Game Boy Advance (mGBA core). The design lets other cores opt in later.

## How to use it

1. Open a GBA game.
2. Open the in-game menu (the menu button on the touch overlay, or the gamepad shortcut).
3. Tap **Cheats**.
4. Tap **Add cheat**, give it a name, paste the code(s), pick a format (or leave **Auto**) and tap **Save**.
   Multi-line codes are supported: one code per line.
5. Use the switch next to each cheat to enable or disable it. Tap a cheat to edit or delete it.
6. Close the menu. Lemuroid re-applies the enabled cheats immediately, without restarting the game,
   and shows a toast such as "2 cheats active".

Cheats are stored per game and restored automatically the next time the same game is opened.

## Supported formats

| Format in the editor       | Shape of one line            | Notes                                                        |
|----------------------------|------------------------------|--------------------------------------------------------------|
| Auto (default)             | any of the shapes below      | Each line is classified by its shape.                        |
| GameShark / Action Replay  | `XXXXXXXX YYYYYYYY` (8 + 8)  | GameShark v1/v2 and Action Replay v3. mGBA tells them apart. |
| CodeBreaker                | `XXXXXXXX YYYY` (8 + 4)      |                                                              |
| Raw (address:value)        | `AAAAAAAA:VV`, `:VVVV`, `:VVVVVVVV` | VBA-style raw memory patch.                           |

Lines may be entered with or without the space, in upper or lower case, and separated by new lines,
`+` or `;`. Lines starting with `#` or `//` are ignored. Choosing an explicit format only adds
validation: mGBA always auto-detects the encryption of a line itself.

Codes that need a "master" / enable code: paste the master code as the first line of the cheat, the
same way you would in mGBA or RetroArch.

## What changed

Library (`retrograde-app-shared`):

- `lib/cheats/Cheat.kt`: the `Cheat` model and `CheatFormat` enum (kotlinx.serialization).
- `lib/cheats/CheatCompiler.kt`: interface that turns user text into the exact string a core expects,
  with typed errors (`Empty`, `InvalidLine`, `FormatMismatch`).
- `lib/cheats/GbaCheatCompiler.kt`: the mGBA implementation (validation + canonical shapes).
- `lib/cheats/CheatCompilers.kt`: core → compiler registry.
- `lib/cheats/CheatsSerializer.kt`: damage-tolerant JSON encoding of a game's cheat list.
- `lib/cheats/CheatsManager.kt`: one atomic JSON file per game and core under
  `<app external files>/cheats/<core>/<rom file name>.cheats.json`.
- `SystemCoreConfig.cheatsSupported` flag, set to `true` for mGBA in `GameSystem`.
- `DirectoriesManager.getCheatsDirectory()`.

App (`lemuroid-app`):

- `shared/game/viewmodel/GameViewModelCheats.kt`: applies the enabled cheats to the core at the right
  moments (first rendered frame, after an auto-save restore, after loading a state, after restart,
  after returning from the menu). Reset + re-add runs atomically on the emulation thread.
- `BaseGameScreenViewModel` / `BaseGameActivity`: wiring of the above.
- `mobile/feature/gamemenu/cheats/`: the **Cheats** screen and its view model (Compose, Material 3).
- `GameMenuHomeScreen` / `GameMenuNavigationRoutes` / `GameMenuActivity`: the new menu entry and route.
- `LemuroidApplicationModule`: Dagger provider for `CheatsManager`.
- New strings and the `ic_menu_cheats` icon.

Tests (`retrograde-app-shared/src/test`): JUnit tests for the compiler and the serializer.

## Why it is built this way

- **mGBA ignores the `enabled` flag of `retro_cheat_set`**, and its only removal primitive is
  `retro_cheat_reset`. Disabling one cheat therefore means: reset, then re-add every enabled cheat.
  `GameViewModelCheats` does exactly that and only when the enabled set actually changed.
- **mGBA re-splits the string it receives into lines of exactly 13 or 17 characters** and silently
  drops everything else, and it never reports parse failures through the libretro API. All validation
  and canonicalisation therefore happens in `GbaCheatCompiler` before anything reaches the core, and
  the app reports what it did (and what it skipped) with toasts instead of assuming success.
- Cheats are **not** written into save files, save states or SRAM. Loading an old state works as
  before; the enabled cheats are simply re-applied afterwards.
- Persistence is a per-game JSON file next to the save states instead of a new Room table: no database
  migration risk, trivially readable, and a damaged file degrades to "no cheats" instead of a crash.

## Adding another core later

1. Write a `CheatCompiler` that matches that core's `retro_cheat_set` parser (read its `libretro.c`).
2. Register it in `CheatCompilers.forCore`.
3. Set `cheatsSupported = true` on the matching `SystemCoreConfig`.

Nothing else needs to change: the menu entry, persistence and lifecycle handling are generic.

## Known limitations

- Only GBA (mGBA) is enabled. Other cores keep their previous behaviour.
- The TV (leanback) game menu does not show the Cheats entry yet. Cheats configured on a phone still
  apply when the same ROM is launched through the TV UI, because the game screen is shared.
- The core gives no feedback about whether an individual code had any effect. A well-formed code for
  the wrong game or region is accepted and simply does nothing.
- Lemuroid's save-sync feature does not sync the cheats directory.
- The libretro API carries no cheat format information, so the format chosen in the editor is used for
  validation only; mGBA auto-detects GameShark vs Action Replay encryption per line.

## Building

The project builds like upstream Lemuroid. For a debug APK with bundled cores:

```
git submodule update --init
./gradlew assembleFreeBundleDebug
# output: lemuroid-app/build/outputs/apk/freeBundle/debug/lemuroid-app-free-bundle-debug.apk
```

Requirements: JDK 17+, Android SDK with platform 35 and build-tools 34.0.0, and network access to
`dl.google.com` (SDK and AndroidX artifacts), `jitpack.io` (LibretroDroid) and Maven Central.
Unit tests: `./gradlew :retrograde-app-shared:testDebugUnitTest`.
