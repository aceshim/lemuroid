package com.swordfish.lemuroid.lib.cheats.presets

import com.swordfish.lemuroid.lib.cheats.CheatFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** One entry of the catalog file. `code` is a list of lines so the file stays readable on GitHub. */
@Serializable
data class CheatPresetEntry(
    val systemId: String,
    val titlePattern: String,
    val region: String,
    val name: String,
    val code: List<String>,
    val format: CheatFormat = CheatFormat.AUTO,
    val notes: String = "",
)

/** Catalog file: `{"version": 1, "presets": [ ... ]}`. Damaged entries are skipped, not fatal. */
object CheatPresetsSerializer {
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

    fun decode(text: String): List<CheatPreset> {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
        val entries = runCatching { root["presets"]?.jsonArray }.getOrNull() ?: return emptyList()

        return entries.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(CheatPresetEntry.serializer(), element) }
                .mapCatching { it.toPreset() }
                .getOrNull()
        }
    }

    private fun CheatPresetEntry.toPreset(): CheatPreset? {
        if (name.isBlank() || code.isEmpty()) return null
        return CheatPreset(
            systemId = systemId,
            titlePattern = Regex(titlePattern, RegexOption.IGNORE_CASE),
            region = region,
            name = name,
            code = code.joinToString("\n"),
            format = format,
            notes = notes,
        )
    }
}
