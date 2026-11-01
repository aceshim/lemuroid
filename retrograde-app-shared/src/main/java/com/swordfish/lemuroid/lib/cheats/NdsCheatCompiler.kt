package com.swordfish.lemuroid.lib.cheats

/**
 * Cheat compiler for the melonDS libretro core (Nintendo DS), Action Replay DS codes.
 *
 * The port's `retro_cheat_set` splits the string on spaces and `+`, parses every token as a 32-bit hex
 * number into a fixed buffer of 128 words (64 lines) without bounds checks, copies the first 128 bytes
 * of the string into the code name, and then executes the code exactly once. Hence:
 * - every line is canonicalized to `XXXXXXXX YYYYYYYY`,
 * - a cheat is capped at [MAX_LINES] lines,
 * - the final string is padded with spaces to [MIN_CODE_LENGTH] characters so the name copy never
 *   reads past the end of the string,
 * - [applyMode] is [CheatApplyMode.EVERY_FRAME], so the app keeps re-sending enabled cheats.
 */
object NdsCheatCompiler : CheatCompiler {
    private val HEX_16 = Regex("^[0-9A-F]{16}$")

    const val MAX_LINES = 64
    const val MIN_CODE_LENGTH = 128

    override val supportedFormats: List<CheatFormat> =
        listOf(CheatFormat.AUTO, CheatFormat.ACTION_REPLAY_DS)

    override val applyMode: CheatApplyMode = CheatApplyMode.EVERY_FRAME

    override fun compile(
        code: String,
        format: CheatFormat,
    ): CheatCompilationResult {
        if (format != CheatFormat.AUTO && format != CheatFormat.ACTION_REPLAY_DS) {
            return CheatCompilationResult.Failure(CheatError.FormatMismatch(1, code.lines().first(), format))
        }

        val lines = GbaCheatCompiler.splitLines(code)
        if (lines.isEmpty()) {
            return CheatCompilationResult.Failure(CheatError.Empty)
        }

        val compiled = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            val compact = line.filterNot { it.isWhitespace() }.uppercase()
            if (!HEX_16.matches(compact)) {
                return CheatCompilationResult.Failure(CheatError.InvalidLine(index + 1, line))
            }
            if (compiled.size >= MAX_LINES) {
                return CheatCompilationResult.Failure(CheatError.TooLong(MAX_LINES))
            }
            compiled.add("${compact.substring(0, 8)} ${compact.substring(8)}")
        }

        val libretroCode = compiled.joinToString("+").padEnd(MIN_CODE_LENGTH, ' ')
        return CheatCompilationResult.Success(compiled, libretroCode)
    }
}
