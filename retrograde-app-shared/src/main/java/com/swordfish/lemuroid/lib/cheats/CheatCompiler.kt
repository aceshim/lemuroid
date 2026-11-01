package com.swordfish.lemuroid.lib.cheats

/**
 * Translates the text a user typed into the exact string a libretro core expects in `retro_cheat_set`.
 *
 * Every core has its own parser with its own quirks, so each supported core gets one implementation.
 * Validation happens here because most cores (mGBA included) silently ignore lines they cannot parse.
 */
interface CheatCompiler {
    /** Formats this core understands. Always contains [CheatFormat.AUTO]. */
    val supportedFormats: List<CheatFormat>

    /** How the core treats a cheat once it has been handed over. */
    val applyMode: CheatApplyMode

    /** Validates [code] against [format] and returns the canonical lines, or the first error found. */
    fun compile(
        code: String,
        format: CheatFormat,
    ): CheatCompilationResult
}

/** What a core does with a cheat passed to `retro_cheat_set`. */
enum class CheatApplyMode {
    /** The core keeps the cheat and runs it itself every frame until `retro_cheat_reset` (mGBA). */
    PERSISTENT,

    /**
     * The core executes the code once and forgets it (the melonDS libretro port). The app has to keep
     * re-sending the enabled cheats while the game runs.
     */
    EVERY_FRAME,
}

sealed class CheatCompilationResult {
    /**
     * [lines] are the canonical lines, [libretroCode] is the exact string to pass to `retro_cheat_set`
     * (lines joined with `+`, the libretro convention, plus any core specific padding).
     */
    data class Success(
        val lines: List<String>,
        val libretroCode: String,
    ) : CheatCompilationResult()

    data class Failure(val error: CheatError) : CheatCompilationResult()
}

sealed class CheatError {
    /** The code is empty or only contains blank lines. */
    data object Empty : CheatError()

    /** [line] (1-based [lineNumber]) does not match any format the core supports. */
    data class InvalidLine(val lineNumber: Int, val line: String) : CheatError()

    /** [line] is a valid code, but not in the [expected] format the user selected. */
    data class FormatMismatch(val lineNumber: Int, val line: String, val expected: CheatFormat) : CheatError()

    /** The cheat has more lines than the core can hold. */
    data class TooLong(val maxLines: Int) : CheatError()
}
