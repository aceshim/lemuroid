package com.swordfish.lemuroid.lib.cheats.presets

import com.swordfish.lemuroid.lib.cheats.CheatFormat

/**
 * A ready-made cheat the user can add from a list instead of typing it.
 *
 * Codes are community codes collected from public cheat databases. They are tied to a game and a
 * region, and they are not verified by Lemuroid: a code for the wrong region simply does nothing.
 */
data class CheatPreset(
    val systemId: String,
    val titlePattern: Regex,
    val region: String,
    val name: String,
    val code: String,
    val format: CheatFormat,
    val notes: String,
)

object CheatPresets {
    private const val NDS = "nds"

    private val POKEMON_HGSS = Regex("heart ?gold|soul ?silver|하트 ?골드|소울 ?실버")

    val all: List<CheatPreset> =
        listOf(
            CheatPreset(
                systemId = NDS,
                titlePattern = POKEMON_HGSS,
                region = "USA",
                name = "Max Money",
                code =
                    """
                    94000130 FCFF0000
                    62111880 00000000
                    B2111880 00000000
                    00000088 000F423F
                    D2000000 00000000
                    """.trimIndent(),
                format = CheatFormat.ACTION_REPLAY_DS,
                notes = "Hold L+R in game (게임 중 L+R 길게 누르기)",
            ),
            CheatPreset(
                systemId = NDS,
                titlePattern = POKEMON_HGSS,
                region = "USA",
                name = "Rare Candy x999 (Medicine pocket, slot 1)",
                code =
                    """
                    94000130 FCFF0000
                    B2111880 00000000
                    00000B74 03E70032
                    D2000000 00000000
                    """.trimIndent(),
                format = CheatFormat.ACTION_REPLAY_DS,
                notes = "Hold L+R in game. Replaces slot 1 of the Medicine pocket (약 주머니 1번 칸을 덮어씀)",
            ),
            CheatPreset(
                systemId = NDS,
                titlePattern = POKEMON_HGSS,
                region = "USA",
                name = "Walk Through Walls",
                code =
                    """
                    94000130 FCFD0200
                    1205DAA2 00000200
                    D2000000 00000000
                    94000130 FCFD0100
                    1205DAA2 00001C20
                    D2000000 00000000
                    """.trimIndent(),
                format = CheatFormat.ACTION_REPLAY_DS,
                notes = "R+B to enable, L+B to disable. Save first (R+B 켜기, L+B 끄기. 먼저 저장하세요)",
            ),
        )

    /**
     * Presets for a game, matched on its system and on its title or file name. Presets whose region
     * appears in the title (e.g. "(USA)") come first.
     */
    fun forGame(
        systemId: String,
        title: String,
        fileName: String,
    ): List<CheatPreset> {
        val haystack = "${title.lowercase()} ${fileName.lowercase()}"
        return all
            .filter { it.systemId == systemId && it.titlePattern.containsMatchIn(haystack) }
            .sortedByDescending { haystack.contains(it.region.lowercase()) }
    }
}
