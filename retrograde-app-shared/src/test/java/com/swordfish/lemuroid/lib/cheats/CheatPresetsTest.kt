package com.swordfish.lemuroid.lib.cheats

import com.swordfish.lemuroid.lib.cheats.presets.CheatPresets
import com.swordfish.lemuroid.lib.cheats.presets.CheatPresetsSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheatPresetsTest {
    private fun forGame(
        systemId: String,
        title: String,
        fileName: String,
    ) = CheatPresets.forGame(CheatPresets.bundled, systemId, title, fileName)

    @Test
    fun bundledCatalogLoadsFromResources() {
        assertEquals(17, CheatPresets.bundled.size)
    }

    @Test
    fun catalogSerializerSkipsDamagedEntriesAndRejectsGarbage() {
        val text =
            """
            {"version": 1, "presets": [
              {"systemId": "nds", "titlePattern": "heart ?gold", "region": "Korea", "name": "ok", "code": ["12002346 00002100"]},
              {"systemId": "nds", "name": "no code", "code": []},
              {"broken": true},
              "garbage"
            ]}
            """.trimIndent()
        val decoded = CheatPresetsSerializer.decode(text)
        assertEquals(1, decoded.size)
        assertEquals("ok", decoded[0].name)
        assertEquals(CheatFormat.AUTO, decoded[0].format)
        assertTrue(decoded[0].titlePattern.containsMatchIn("pokemon heartgold"))
        assertEquals(emptyList<Any>(), CheatPresetsSerializer.decode("nope"))
        assertEquals(emptyList<Any>(), CheatPresetsSerializer.decode("{\"presets\": 3}"))
    }

    @Test
    fun heartGoldPresetsMatchByTitleOrFileName() {
        assertTrue(forGame("nds", "Pokemon - HeartGold Version (USA)", "x.nds").isNotEmpty())
        assertTrue(forGame("nds", "x", "Pokemon SoulSilver.nds").isNotEmpty())
        assertTrue(forGame("nds", "포켓몬스터 하트골드", "hg.nds").isNotEmpty())
        assertTrue(forGame("nds", "Mario Kart DS (USA)", "mkds.nds").isEmpty())
        assertTrue(forGame("gba", "Pokemon - HeartGold Version (USA)", "x.gba").isEmpty())
    }

    @Test
    fun koreanTitlesPutKoreanPresetsFirstAndUsTitlesPutUsPresetsFirst() {
        val korean = forGame("nds", "포켓몬스터 하트골드", "hg_kor.nds")
        assertEquals("Korea", korean.first().region)
        assertTrue(korean.any { it.name == "게임 고속화" })
        assertTrue(korean.any { it.name == "흑화 방지 (하트골드)" })
        assertTrue(korean.none { it.name == "흑화 방지 (소울실버)" })

        val us = forGame("nds", "Pokemon - HeartGold Version (USA)", "x.nds")
        assertEquals("USA", us.first().region)

        val koreaTag = forGame("nds", "Pokemon - SoulSilver Version (Korea)", "x.nds")
        assertEquals("Korea", koreaTag.first().region)
        assertTrue(koreaTag.any { it.name == "흑화 방지 (소울실버)" })
    }

    @Test
    fun everyPresetCompilesWithItsCore() {
        CheatPresets.bundled.forEach { preset ->
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
        val keys = CheatPresets.bundled.map { Triple(it.systemId, it.region, it.name) }
        assertEquals(keys.size, keys.toSet().size)
    }
}
