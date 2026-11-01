package com.swordfish.lemuroid.lib.cheats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NdsCheatCompilerTest {
    private val compiler = NdsCheatCompiler

    private val maxMoney =
        """
        94000130 FCFF0000
        62111880 00000000
        B2111880 00000000
        00000088 000F423F
        D2000000 00000000
        """.trimIndent()

    private fun success(
        code: String,
        format: CheatFormat = CheatFormat.AUTO,
    ): CheatCompilationResult.Success {
        val result = compiler.compile(code, format)
        assertTrue("Expected success but got $result", result is CheatCompilationResult.Success)
        return result as CheatCompilationResult.Success
    }

    private fun failure(
        code: String,
        format: CheatFormat = CheatFormat.AUTO,
    ): CheatError {
        val result = compiler.compile(code, format)
        assertTrue("Expected failure but got $result", result is CheatCompilationResult.Failure)
        return (result as CheatCompilationResult.Failure).error
    }

    @Test
    fun actionReplayLinesAreCanonicalized() {
        val lines = success("94000130fcff0000\n6211 1880 0000 0000\n  B2111880 00000000").lines
        assertEquals(listOf("94000130 FCFF0000", "62111880 00000000", "B2111880 00000000"), lines)
    }

    @Test
    fun libretroCodeJoinsWithPlusAndIsPaddedForTheNameCopy() {
        val result = success(maxMoney)
        assertTrue(result.libretroCode.startsWith("94000130 FCFF0000+62111880 00000000+"))
        assertTrue(result.libretroCode.length >= NdsCheatCompiler.MIN_CODE_LENGTH)
        assertEquals(result.libretroCode.trimEnd(), result.lines.joinToString("+"))
        // Every token the core will parse is 8 hex digits
        result.libretroCode.trim().split('+', ' ').forEach {
            assertTrue("Bad token '$it'", Regex("^[0-9A-F]{8}$").matches(it))
        }
    }

    @Test
    fun longCodesAreNotPaddedTwice() {
        val longCode = (1..20).joinToString("\n") { "94000130 FCFF0000" }
        val result = success(longCode)
        assertEquals(20, result.lines.size)
        assertEquals(result.lines.joinToString("+"), result.libretroCode)
    }

    @Test
    fun libretroChtStringsWithPlusSeparatedTokensAreAccepted() {
        val cht = "12000E02+0000E000+120543DC+00004770"
        assertEquals(listOf("12000E02 0000E000", "120543DC 00004770"), success(cht).lines)
        assertEquals(
            listOf("12000E02 0000E000", "120543DC 00004770"),
            success("12000E020000E000120543DC00004770").lines,
        )
    }

    @Test
    fun oddNumberOfWordsIsRejected() {
        assertEquals(CheatError.InvalidLine(1, "12000E02 0000E000 120543DC"), failure("12000E02 0000E000 120543DC"))
        assertEquals(CheatError.InvalidLine(1, "12000E02 0000E0000"), failure("12000E02 0000E0000"))
    }

    @Test
    fun invalidLinesAreRejected() {
        assertEquals(CheatError.InvalidLine(1, "94000130 FCFF"), failure("94000130 FCFF"))
        assertEquals(CheatError.InvalidLine(2, "02023D50:0F"), failure("94000130 FCFF0000\n02023D50:0F"))
        assertEquals(CheatError.Empty, failure("\n\n"))
    }

    @Test
    fun gbaFormatsAreRejectedForDs() {
        val error = failure("94000130 FCFF0000", CheatFormat.GAMESHARK)
        assertTrue(error is CheatError.FormatMismatch)
    }

    @Test
    fun cheatsAreCappedAtTheCoreBufferSize() {
        val ok = (1..NdsCheatCompiler.MAX_LINES).joinToString("\n") { "94000130 FCFF0000" }
        assertEquals(NdsCheatCompiler.MAX_LINES, success(ok).lines.size)

        val tooLong = ok + "\n94000130 FCFF0000"
        assertEquals(CheatError.TooLong(NdsCheatCompiler.MAX_LINES), failure(tooLong))
    }

    @Test
    fun applyModeIsEveryFrame() {
        assertEquals(CheatApplyMode.EVERY_FRAME, compiler.applyMode)
        assertEquals(CheatApplyMode.PERSISTENT, GbaCheatCompiler.applyMode)
    }
}
