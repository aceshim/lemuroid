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

    /** Validates [code] against [format] and returns the canonical lines, or the first error found. */
    fun compile(
        code: String,
        format: CheatFormat,
    ): CheatCompilationResult
}

sealed class CheatCompilationResult {
    data class Success(val lines: List<String>) : CheatCompilationResult() {
        /** Lines joined with `+`, the libretro convention for passing a multi-line cheat in one call. */
        val libretroCode: String
            get() = lines.joinToString("+")
    }

    data class Failure(val error: CheatError) : CheatCompilationResult()
}

sealed class CheatError {
    /** The code is empty or only contains blank lines. */
    data object Empty : CheatError()

    /** [line] (1-based [lineNumber]) does not match any format the core supports. */
    data class InvalidLine(val lineNumber: Int, val line: String) : CheatError()

    /** [line] is a valid code, but not in the [expected] format the user selected. */
    data class FormatMismatch(val lineNumber: Int, val line: String, val expected: CheatFormat) : CheatError()
}
