package com.swordfish.lemuroid.lib.cheats

import com.swordfish.lemuroid.lib.cheats.presets.CheatPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheatPresetsTest {
    @Test
    fun heartGoldPresetsMatchByTitleOrFileName() {
        assertTrue(CheatPresets.forGame("nds", "Pokemon - HeartGold Version (USA)", "x.nds").isNotEmpty())
        assertTrue(CheatPresets.forGame("nds", "x", "Pokemon SoulSilver.nds").isNotEmpty())
        assertTrue(CheatPresets.forGame("nds", "포켓몬스터 하트골드", "hg.nds").isNotEmpty())
        assertTrue(CheatPresets.forGame("nds", "Mario Kart DS (USA)", "mkds.nds").isEmpty())
        assertTrue(CheatPresets.forGame("gba", "Pokemon - HeartGold Version (USA)", "x.gba").isEmpty())
    }

    @Test
    fun everyPresetCompilesWithItsCore() {
        CheatPresets.all.forEach { preset ->
            val compiler =
                when (preset.systemId) {
                    "nds" -> NdsCheatCompiler
                    "gba" -> GbaCheatCompiler
                    else -> throw AssertionError("Unknown system ${preset.systemId}")
                }
            val result = compiler.compile(preset.code, preset.format)
            assertTrue("${preset.name} does not compile: $result", result is CheatCompilationResult.Success)
        }
    }

    @Test
    fun presetsHaveUniqueNamesPerGameAndRegion() {
        val keys = CheatPresets.all.map { Triple(it.systemId, it.region, it.name) }
        assertEquals(keys.size, keys.toSet().size)
    }
}
