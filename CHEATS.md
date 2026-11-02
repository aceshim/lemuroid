# Cheat support (Game Boy Advance and Nintendo DS)

This fork adds a small, native cheat manager to Lemuroid. It reuses the libretro cheat API that the
emulator cores already implement (`retro_cheat_set` / `retro_cheat_reset`, exposed by LibretroDroid),
keeps everything local, and leaves the rest of Lemuroid untouched.

Supported today: Game Boy Advance (mGBA core) and Nintendo DS (melonDS core). The design lets other
cores opt in later.

## How to use it

1. Open a GBA or DS game.
2. Open the in-game menu (the menu button on the touch overlay, or the gamepad shortcut).
3. Tap **Cheats** (between Fast-Forward and Edit Controls).
4. Either tap **Add from list** and tick the ready-made cheats for this game (shown only when the
   catalog has entries for the game), or tap **Add cheat**, give it a name, paste the code(s), pick a
   format (or leave **Auto**) and tap **Save**. Multi-line codes are supported: one code per line.
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
| Raw (address:value)        | `AAAAAAAA:VV`, `:VVVV`, `:VVVVVVVV` | VBA-style raw memory patch. GBA only.                 |
| Action Replay DS           | `XXXXXXXX YYYYYYYY` (8 + 8)  | Nintendo DS only. At most 64 lines per cheat.                |

Lines may be entered with or without the space, in upper or lower case, and separated by new lines,
`+` or `;`. Lines starting with `#` or `//` are ignored. Choosing an explicit format only adds
validation: mGBA always auto-detects the encryption of a line itself.

Codes that need a "master" / enable code: paste the master code as the first line of the cheat, the
same way you would in mGBA or RetroArch.

### Built-in list and GitHub catalog

The catalog is one JSON file: `retrograde-app-shared/src/main/resources/cheats/presets.json`. It is
bundled with the app, and the same file is downloaded from this repository on GitHub every time the
Cheats screen opens (and with the **Update list** button in the picker), so editing it on GitHub
updates every phone without a rebuild. The download URL is `CheatPresetsRepository.DEFAULT_CATALOG_URL`;
a failed or invalid download keeps the previous list.

Entry format:

```json
{
  "systemId": "nds",
  "titlePattern": "heart ?gold|soul ?silver|하트 ?골드|소울 ?실버",
  "region": "Korea",
  "name": "게임 고속화",
  "format": "ACTION_REPLAY_DS",
  "notes": "shown under the name in the picker",
  "code": ["12000E02 0000E000", "120543DC 00004770"]
}
```

`titlePattern` is a case-insensitive regex matched against the game's title and file name. Presets
whose region matches the title (or a Korean title for Korean presets) are listed first.

Today it holds Pokémon HeartGold / SoulSilver codes for the Korean release (message speed, game speed,
movement x2, screen transitions, EXP bar, instant turning, walk through walls, max money, all
medicine, all Poké Balls, all items, all TMs/HMs, EXP x2 / x4, guaranteed catch, wild 6 IV, wild
encounter rate high/low/normal, learn any TM, forget HMs, in-battle HP, re-receive the starter,
anti-freeze) and the US release (text speed, movement x2, max money, rare candy, walk through walls). They come from public cheat lists and are **not verified** by this
project. The Korean "게임 고속화" code had a 9-digit value in its source and was corrected to a 16-bit
value (`0000E000`). The "wild encounter rate low" trigger in the source was a plain SELECT mask and was
corrected to SELECT+Left (`FFDB0000`) to match its label. "게임 배속" exists in two variants (v1 from the
community list, v2 shared by a Delta user); enable only one of them.

## What changed

Library (`retrograde-app-shared`):

- `lib/cheats/Cheat.kt`: the `Cheat` model and `CheatFormat` enum (kotlinx.serialization).
- `lib/cheats/CheatCompiler.kt`: interface that turns user text into the exact string a core expects,
  with typed errors (`Empty`, `InvalidLine`, `FormatMismatch`).
- `lib/cheats/GbaCheatCompiler.kt`: the mGBA implementation (validation + canonical shapes).
- `lib/cheats/NdsCheatCompiler.kt`: the melonDS DS implementation (Action Replay DS, line cap).
- `lib/cheats/CheatCompilers.kt`: core → compiler registry, plus the `CheatApplyMode` each core needs.
- `lib/cheats/presets/`: the cheat catalog (`presets.json` resource, serializer, matcher) and the
  `CheatPresetsRepository` that downloads it from GitHub and caches it next to the cheat files.
- `lib/cheats/CheatsSerializer.kt`: damage-tolerant JSON encoding of a game's cheat list.
- `lib/cheats/CheatsManager.kt`: one atomic JSON file per game and core under
  `<app external files>/cheats/<core>/<rom file name>.cheats.json`.
- `SystemCoreConfig.cheatsSupported` flag, set to `true` for mGBA and melonDS in `GameSystem`.
- `DirectoriesManager.getCheatsDirectory()`.

App (`lemuroid-app`):

- `shared/game/viewmodel/GameViewModelCheats.kt`: applies the enabled cheats to the core at the right
  moments (first rendered frame, after an auto-save restore, after loading a state, after restart,
  after returning from the menu). Reset + re-add runs atomically on the emulation thread.
- `BaseGameScreenViewModel` / `BaseGameActivity`: wiring of the above.
- `mobile/feature/gamemenu/cheats/`: the **Cheats** screen, the preset picker and the editor
  (Compose, Material 3) with their view model.
- `GameMenuHomeScreen` / `GameMenuNavigationRoutes` / `GameMenuActivity`: the new menu entry and route.
- `LemuroidApplicationModule`: Dagger provider for `CheatsManager`.
- New strings (English and Korean) and the `ic_menu_cheats` icon.

Tests (`retrograde-app-shared/src/test`): JUnit tests for the compiler and the serializer.

## Why it is built this way

- **mGBA ignores the `enabled` flag of `retro_cheat_set`**, and its only removal primitive is
  `retro_cheat_reset`. Disabling one cheat therefore means: reset, then re-add every enabled cheat.
  `GameViewModelCheats` does exactly that and only when the enabled set actually changed.
- **mGBA re-splits the string it receives into lines of exactly 13 or 17 characters** and silently
  drops everything else, and it never reports parse failures through the libretro API. All validation
  and canonicalisation therefore happens in `GbaCheatCompiler` before anything reaches the core, and
  the app reports what it did (and what it skipped) with toasts instead of assuming success.
- **The DS core is "melonDS DS" (JesseTG's port), not the older libretro/melonDS port.** It keeps a
  list of cheats, runs them every frame, honours the `enabled` flag and clears the list on
  `retro_cheat_reset`, so DS uses the same reset-and-reapply path as GBA. It validates every string
  against `^\s*[0-9A-Fa-f]{8}([+\s-]*[0-9A-Fa-f]{8})*$` and silently ignores anything else (the
  warning goes to a message channel Lemuroid does not display). `NdsCheatCompiler` therefore hands
  over exactly `XXXXXXXX YYYYYYYY` lines joined with `+`, nothing more. An earlier revision padded the
  string with spaces for the older port's parser; that made the real core reject every DS cheat.
- Cheats are **not** written into save files, save states or SRAM. Loading an old state works as
  before; the enabled cheats are simply re-applied afterwards.
- Persistence is a per-game JSON file next to the save states instead of a new Room table: no database
  migration risk, trivially readable, and a damaged file degrades to "no cheats" instead of a crash.

## Adding another core later

1. Write a `CheatCompiler` that matches that core's `retro_cheat_set` parser (read its `libretro.c`),
   and declare whether the core keeps cheats (`PERSISTENT`, both bundled cores) or runs them once per
   call (`EVERY_FRAME`, kept for cores that behave like the old libretro/melonDS port).
2. Register it in `CheatCompilers.forCore`.
3. Set `cheatsSupported = true` on the matching `SystemCoreConfig`.

Nothing else needs to change: the menu entry, persistence and lifecycle handling are generic.

## Known limitations

- Only GBA (mGBA) and DS (melonDS) are enabled. Other cores keep their previous behaviour.
- There is no Action Replay code that makes HeartGold / SoulSilver run faster; use Lemuroid's own
  Fast-Forward toggle (2x) in the same menu, and set the in-game text speed to Fast.
- The TV (leanback) game menu does not show the Cheats entry yet. Cheats configured on a phone still
  apply when the same ROM is launched through the TV UI, because the game screen is shared.
- The core gives no feedback about whether an individual code had any effect. A well-formed code for
  the wrong game or region is accepted and simply does nothing.
- Lemuroid's save-sync feature does not sync the cheats directory.
- The catalog download is the one network call this feature makes. It is a plain GET of a public
  file on GitHub; nothing about the user or the game is sent.
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

## Verification status

- `:retrograde-app-shared:testDebugUnitTest`: 39 tests pass (GBA compiler 17, DS compiler 9,
  serializer 7, presets 6).
- `:lemuroid-app:assembleFreeBundleDebug`: builds; the debug APK bundles all cores and installs as
  `com.swordfish.lemuroid.debug` next to the stock app.
- Not yet done: on-device checks (effect in game, state load, restart, save files). Run them on a
  phone with a homebrew or your own ROM before relying on a code.
