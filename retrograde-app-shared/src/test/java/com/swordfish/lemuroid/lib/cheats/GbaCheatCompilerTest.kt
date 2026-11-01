package com.swordfish.lemuroid.lib.cheats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GbaCheatCompilerTest {
    private val compiler = GbaCheatCompiler

    private fun compileOk(
        code: String,
        format: CheatFormat = CheatFormat.AUTO,
    ): List<String> {
        val result = compiler.compile(code, format)
        assertTrue("Expected success but got $result", result is CheatCompilationResult.Success)
        return (result as CheatCompilationResult.Success).lines
    }

    private fun compileError(
        code: String,
        format: CheatFormat = CheatFormat.AUTO,
    ): CheatError {
        val result = compiler.compile(code, format)
        assertTrue("Expected failure but got $result", result is CheatCompilationResult.Failure)
        return (result as CheatCompilationResult.Failure).error
    }

    @Test
    fun gameSharkCodeWithSpaceIsKeptCanonical() {
        assertEquals(listOf("82025BB8 0000270F"), compileOk("82025BB8 0000270F"))
    }

    @Test
    fun gameSharkCodeWithoutSpaceGetsTheSpaceMgbaRequires() {
        assertEquals(listOf("82025BB8 0000270F"), compileOk("82025BB80000270F"))
    }

    @Test
    fun lowercaseAndExtraWhitespaceAreNormalized() {
        assertEquals(listOf("82025BB8 0000270F"), compileOk("  82025bb8\t  0000270f  "))
    }

    @Test
    fun codeBreakerCodeIsCanonicalized() {
        assertEquals(listOf("82025BB8 270F"), compileOk("82025bb8270f"))
        assertEquals(listOf("82025BB8 270F"), compileOk("82025BB8 270F"))
    }

    @Test
    fun rawCodesArePaddedToTheLengthsMgbaSplitsOn() {
        assertEquals(listOf("02023D50:0F  "), compileOk("02023D50:0F"))
        assertEquals(listOf("02023D50:270F"), compileOk("02023D50:270F"))
        assertEquals(listOf("02023D50:0000270F"), compileOk("02023D50:0000270F"))
        assertEquals(13, compileOk("02023D50:0F").first().length)
        assertEquals(17, compileOk("02023D50:0000270F").first().length)
    }

    @Test
    fun multilineCodesAreSplitOnNewlinesPlusAndSemicolons() {
        val code = "82025BB8 0000270F\n82025BBA 0000270F\r\n\n82025BBC0000270F+82025BBE 270F;82025BC0 270F"
        assertEquals(
            listOf(
                "82025BB8 0000270F",
                "82025BBA 0000270F",
                "82025BBC 0000270F",
                "82025BBE 270F",
                "82025BC0 270F",
            ),
            compileOk(code),
        )
    }

    @Test
    fun commentsAndBlankLinesAreIgnored() {
        val code = "# Infinite money\n\n// second line\n82025BB8 0000270F\n   \n"
        assertEquals(listOf("82025BB8 0000270F"), compileOk(code))
    }

    @Test
    fun libretroCodeJoinsLinesWithPlus() {
        val result = compiler.compile("82025BB8 0000270F\n82025BBA 270F", CheatFormat.AUTO)
        assertEquals(
            "82025BB8 0000270F+82025BBA 270F",
            (result as CheatCompilationResult.Success).libretroCode,
        )
    }

    @Test
    fun emptyCodeIsRejected() {
        assertEquals(CheatError.Empty, compileError(""))
        assertEquals(CheatError.Empty, compileError("  \n\n# only a comment"))
    }

    @Test
    fun invalidLinesAreReportedWithTheirLineNumber() {
        assertEquals(CheatError.InvalidLine(1, "ZZZZZZZZ 00000000"), compileError("ZZZZZZZZ 00000000"))
        assertEquals(CheatError.InvalidLine(2, "82025BB8 00"), compileError("82025BB8 0000270F\n82025BB8 00"))
        assertEquals(CheatError.InvalidLine(1, "82025BB8"), compileError("82025BB8"))
        assertEquals(CheatError.InvalidLine(1, "82025BB8 0000270F0"), compileError("82025BB8 0000270F0"))
    }

    @Test
    fun rawCodesWith24BitValuesAreRejected() {
        assertEquals(CheatError.InvalidLine(1, "02023D50:00270F"), compileError("02023D50:00270F"))
    }

    @Test
    fun explicitFormatRejectsOtherShapes() {
        assertEquals(
            CheatError.FormatMismatch(1, "82025BB8 270F", CheatFormat.GAMESHARK),
            compileError("82025BB8 270F", CheatFormat.GAMESHARK),
        )
        assertEquals(
            CheatError.FormatMismatch(1, "82025BB8 0000270F", CheatFormat.CODEBREAKER),
            compileError("82025BB8 0000270F", CheatFormat.CODEBREAKER),
        )
        assertEquals(
            CheatError.FormatMismatch(1, "82025BB8 0000270F", CheatFormat.RAW),
            compileError("82025BB8 0000270F", CheatFormat.RAW),
        )
    }

    @Test
    fun explicitFormatAcceptsMatchingShapes() {
        assertEquals(listOf("82025BB8 0000270F"), compileOk("82025BB80000270F", CheatFormat.GAMESHARK))
        assertEquals(listOf("82025BB8 270F"), compileOk("82025BB8270F", CheatFormat.CODEBREAKER))
        assertEquals(listOf("02023D50:0F  "), compileOk("02023d50:0f", CheatFormat.RAW))
    }

    @Test
    fun autoFormatAllowsMixingFormatsAcrossLines() {
        assertEquals(
            listOf("82025BB8 0000270F", "82025BB8 270F", "02023D50:0F  "),
            compileOk("82025BB8 0000270F\n82025BB8 270F\n02023D50:0F"),
        )
    }

    @Test
    fun everyCanonicalLineHasALengthMgbaAccepts() {
        val lines = compileOk("82025BB8 0000270F\n82025BB8 270F\n02023D50:0F\n02023D50:270F\n02023D50:0000270F")
        lines.forEach { assertTrue("Unexpected length for '$it'", it.length == 13 || it.length == 17) }
    }

    @Test
    fun detectFormatClassifiesCompactLines() {
        assertEquals(CheatFormat.GAMESHARK, compiler.detectFormat("82025BB80000270F"))
        assertEquals(CheatFormat.CODEBREAKER, compiler.detectFormat("82025BB8270F"))
        assertEquals(CheatFormat.RAW, compiler.detectFormat("02023D50:0F"))
        assertEquals(null, compiler.detectFormat("82025BB8"))
    }

    @Test
    fun registryExposesTheGbaCompilerOnlyForMgba() {
        assertEquals(GbaCheatCompiler, CheatCompilers.forCore(com.swordfish.lemuroid.lib.library.CoreID.MGBA))
        assertEquals(null, CheatCompilers.forCore(com.swordfish.lemuroid.lib.library.CoreID.GAMBATTE))
    }
}
