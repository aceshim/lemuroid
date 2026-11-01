package com.swordfish.lemuroid.lib.cheats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheatsSerializerTest {
    private val cheats =
        listOf(
            Cheat(
                id = "a",
                name = "Infinite Money",
                code = "82025BB8 0000270F",
                format = CheatFormat.GAMESHARK,
                enabled = true,
            ),
            Cheat(
                id = "b",
                name = "Rare Candy",
                code = "82025BC0 0044\n82025BC2 0001",
                format = CheatFormat.CODEBREAKER,
            ),
            Cheat(
                id = "c",
                name = "Walk Through Walls",
                code = "C518E2595411\n183A3DFD1DD4",
                enabled = false,
            ),
        )

    @Test
    fun roundTripPreservesEveryField() {
        val encoded = CheatsSerializer.encode(cheats)
        assertEquals(cheats, CheatsSerializer.decode(encoded))
    }

    @Test
    fun encodedFileCarriesAVersion() {
        assertTrue(CheatsSerializer.encode(cheats).contains("\"version\": 1"))
    }

    @Test
    fun malformedJsonYieldsAnEmptyList() {
        assertEquals(emptyList<Cheat>(), CheatsSerializer.decode(""))
        assertEquals(emptyList<Cheat>(), CheatsSerializer.decode("not json at all"))
        assertEquals(emptyList<Cheat>(), CheatsSerializer.decode("[1, 2, 3]"))
        assertEquals(emptyList<Cheat>(), CheatsSerializer.decode("{\"cheats\": 42}"))
    }

    @Test
    fun aSingleMalformedEntryIsSkippedWithoutLosingTheOthers() {
        val text =
            """
            {
              "version": 1,
              "cheats": [
                {"id": "a", "name": "Good", "code": "82025BB8 0000270F", "format": "GAMESHARK", "enabled": true},
                {"id": 12, "name": ["broken"]},
                "garbage",
                {"id": "c", "name": "Also good", "code": "82025BB8 270F"}
              ]
            }
            """.trimIndent()

        val decoded = CheatsSerializer.decode(text)
        assertEquals(listOf("a", "c"), decoded.map { it.id })
        assertEquals(CheatFormat.GAMESHARK, decoded[0].format)
        assertTrue(decoded[0].enabled)
        assertEquals(CheatFormat.AUTO, decoded[1].format)
    }

    @Test
    fun unknownFormatsAndUnknownKeysFallBackToDefaults() {
        val text =
            """
            {"version": 99, "future": true, "cheats": [
              {"id": "a", "name": "X", "code": "82025BB8 0000270F", "format": "SOMETHING_NEW", "enabled": true, "extra": 1}
            ]}
            """.trimIndent()

        val decoded = CheatsSerializer.decode(text)
        assertEquals(1, decoded.size)
        assertEquals(CheatFormat.AUTO, decoded[0].format)
        assertTrue(decoded[0].enabled)
    }

    @Test
    fun entriesWithoutIdOrWithDuplicateIdsAreDeduplicated() {
        val text =
            """
            {"cheats": [
              {"id": "", "name": "No id", "code": "82025BB8 0000270F"},
              {"id": "a", "name": "First", "code": "82025BB8 0000270F"},
              {"id": "a", "name": "Duplicate", "code": "82025BB8 0000270F"}
            ]}
            """.trimIndent()

        val decoded = CheatsSerializer.decode(text)
        assertEquals(1, decoded.size)
        assertEquals("First", decoded[0].name)
    }

    @Test
    fun newCheatsGetUniqueIds() {
        val ids = (1..100).map { Cheat().id }.toSet()
        assertEquals(100, ids.size)
    }
}
