package com.swordfish.lemuroid.lib.cheats.presets

import com.swordfish.lemuroid.lib.cheats.CheatFormat

/**
 * A ready-made cheat the user can add from a list instead of typing it.
 *
 * Codes are community codes collected from public cheat lists. They are tied to a game and a region,
 * and they are not verified by Lemuroid: a code for the wrong region simply does nothing.
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

/**
 * The cheat catalog. The bundled copy lives in `src/main/resources/cheats/presets.json`; the same file,
 * hosted on GitHub, can be downloaded at runtime by [CheatPresetsRepository] so that the list can be
 * updated without rebuilding the app.
 */
object CheatPresets {
    const val BUNDLED_RESOURCE = "/cheats/presets.json"

    val bundled: List<CheatPreset> by lazy {
        val text =
            CheatPresets::class.java.getResourceAsStream(BUNDLED_RESOURCE)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return@lazy emptyList()
        CheatPresetsSerializer.decode(text)
    }

    /**
     * Presets for a game, matched on its system and on its title or file name. Presets whose region
     * appears in the title (e.g. "(USA)", "(Korea)", a Korean title) come first.
     */
    fun forGame(
        presets: List<CheatPreset>,
        systemId: String,
        title: String,
        fileName: String,
    ): List<CheatPreset> {
        val haystack = "${title.lowercase()} ${fileName.lowercase()}"
        val looksKorean =
            haystack.contains("kor") ||
                Regex("[(\\[]k[)\\]]").containsMatchIn(haystack) ||
                haystack.any { it in '가'..'힣' }
        return presets
            .filter { it.systemId == systemId && it.titlePattern.containsMatchIn(haystack) }
            .sortedByDescending { preset ->
                when (preset.region.lowercase()) {
                    "korea" -> looksKorean
                    else -> haystack.contains(preset.region.lowercase())
                }
            }
    }
}
