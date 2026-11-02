package com.swordfish.lemuroid.lib.cheats

/**
 * Cheat compiler for the melonDS DS libretro core (Nintendo DS), Action Replay DS codes.
 *
 * Lemuroid bundles the "melonDS DS" core (JesseTG port). Its `retro_cheat_set` validates the whole
 * string against `^\s*[0-9A-Fa-f]{8}([+\s-]*[0-9A-Fa-f]{8})*$`, silently ignores anything else,
 * keeps the accepted cheats in a list that the emulator runs every frame, honours the `enabled` flag
 * and clears the list on `retro_cheat_reset`. So the string handed over is exactly the canonical
 * `XXXXXXXX YYYYYYYY` lines joined with `+`, with nothing before or after, and [applyMode] is
 * [CheatApplyMode.PERSISTENT].
 */
object NdsCheatCompiler : CheatCompiler {
    private val HEX_PAIRS = Regex("^(?:[0-9A-F]{16})+$")

    /** Keeps a single cheat at a size any Action Replay implementation handles. */
    const val MAX_LINES = 64

    override val supportedFormats: List<CheatFormat> =
        listOf(CheatFormat.AUTO, CheatFormat.ACTION_REPLAY_DS)

    override val applyMode: CheatApplyMode = CheatApplyMode.PERSISTENT

    override fun compile(
        code: String,
        format: CheatFormat,
    ): CheatCompilationResult {
        if (format != CheatFormat.AUTO && format != CheatFormat.ACTION_REPLAY_DS) {
            return CheatCompilationResult.Failure(CheatError.FormatMismatch(1, code.lines().first(), format))
        }

        val lines = splitLines(code)
        if (lines.isEmpty()) {
            return CheatCompilationResult.Failure(CheatError.Empty)
        }

        val compiled = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            // Accept "XXXXXXXX YYYYYYYY", "XXXXXXXXYYYYYYYY" and libretro .cht strings ("AAAA+BBBB+CCCC+DDDD").
            val compact = line.filterNot { it.isWhitespace() || it == '+' }.uppercase()
            if (!HEX_PAIRS.matches(compact)) {
                return CheatCompilationResult.Failure(CheatError.InvalidLine(index + 1, line))
            }
            compact.chunked(16).forEach { pair ->
                if (compiled.size >= MAX_LINES) {
                    return CheatCompilationResult.Failure(CheatError.TooLong(MAX_LINES))
                }
                compiled.add("${pair.substring(0, 8)} ${pair.substring(8)}")
            }
        }

        return CheatCompilationResult.Success(compiled, compiled.joinToString("+"))
    }

    /** One entry per text line. `+` is a token separator inside a line (libretro .cht format), not a line break. */
    private fun splitLines(code: String): List<String> {
        return code
            .split('\n', '\r', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
    }
}
