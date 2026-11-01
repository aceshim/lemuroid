package com.swordfish.lemuroid.lib.cheats

/**
 * Cheat compiler for the mGBA libretro core (Game Boy Advance).
 *
 * mGBA's `retro_cheat_set` re-splits the string it receives into lines of exactly 13 characters
 * (`XXXXXXXX XXXX`, CodeBreaker) or 17 characters (`XXXXXXXX XXXXXXXX`, GameShark / Action Replay)
 * and silently drops anything else. The actual format of a line (GameShark v1/v2, Action Replay v3,
 * CodeBreaker, raw) is then auto-detected by the core, so the job of this class is to make sure that
 * every line reaches the core in one of those two canonical shapes.
 *
 * Raw `ADDRESS:VALUE` lines (VBA style) are supported by mGBA's parser too. They are padded with
 * spaces to 13 characters (8-bit values) so that they survive the libretro re-splitting; 16-bit and
 * 32-bit raw lines are already 13 and 17 characters long.
 */
object GbaCheatCompiler : CheatCompiler {
    private val HEX_16 = Regex("^[0-9A-F]{16}$")
    private val HEX_12 = Regex("^[0-9A-F]{12}$")
    private val RAW = Regex("^[0-9A-F]{8}:([0-9A-F]{2}|[0-9A-F]{4}|[0-9A-F]{8})$")

    private const val CODEBREAKER_LINE_LENGTH = 13
    private const val GAMESHARK_LINE_LENGTH = 17

    override val supportedFormats: List<CheatFormat> =
        listOf(CheatFormat.AUTO, CheatFormat.GAMESHARK, CheatFormat.CODEBREAKER, CheatFormat.RAW)

    override val applyMode: CheatApplyMode = CheatApplyMode.PERSISTENT

    override fun compile(
        code: String,
        format: CheatFormat,
    ): CheatCompilationResult {
        val lines = splitLines(code)
        if (lines.isEmpty()) {
            return CheatCompilationResult.Failure(CheatError.Empty)
        }

        val compiled = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            val compact = line.filterNot { it.isWhitespace() }.uppercase()
            val detected =
                detectFormat(compact)
                    ?: return CheatCompilationResult.Failure(CheatError.InvalidLine(index + 1, line))

            if (format != CheatFormat.AUTO && format != detected) {
                return CheatCompilationResult.Failure(CheatError.FormatMismatch(index + 1, line, format))
            }

            compiled.add(canonicalize(compact, detected))
        }

        return CheatCompilationResult.Success(compiled, compiled.joinToString("+"))
    }

    /** Splits user text into candidate lines. Accepts newlines, `+` and `;` as separators and skips comments. */
    fun splitLines(code: String): List<String> {
        return code
            .split('\n', '\r', '+', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
    }

    /** Detects the format of a line with all whitespace removed and letters upper-cased. */
    fun detectFormat(compact: String): CheatFormat? {
        return when {
            RAW.matches(compact) -> CheatFormat.RAW
            HEX_16.matches(compact) -> CheatFormat.GAMESHARK
            HEX_12.matches(compact) -> CheatFormat.CODEBREAKER
            else -> null
        }
    }

    private fun canonicalize(
        compact: String,
        format: CheatFormat,
    ): String {
        return when (format) {
            CheatFormat.GAMESHARK -> "${compact.substring(0, 8)} ${compact.substring(8)}"
            CheatFormat.CODEBREAKER -> "${compact.substring(0, 8)} ${compact.substring(8)}"
            CheatFormat.RAW -> {
                val targetLength =
                    if (compact.length <= CODEBREAKER_LINE_LENGTH) {
                        CODEBREAKER_LINE_LENGTH
                    } else {
                        GAMESHARK_LINE_LENGTH
                    }
                compact.padEnd(targetLength, ' ')
            }
            CheatFormat.AUTO, CheatFormat.ACTION_REPLAY_DS ->
                throw IllegalArgumentException("$format is not a GBA format")
        }
    }
}
