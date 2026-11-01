package com.swordfish.lemuroid.lib.cheats

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** On-disk representation of the cheats of one game. */
@Serializable
data class CheatsConfig(
    val version: Int = CheatsSerializer.CURRENT_VERSION,
    val cheats: List<Cheat> = emptyList(),
)

/**
 * JSON (de)serialization of [CheatsConfig], tolerant to damaged files.
 *
 * A file that cannot be parsed at all yields an empty list. A single malformed entry is skipped so that
 * the remaining cheats of the game survive.
 */
object CheatsSerializer {
    const val CURRENT_VERSION = 1

    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
            encodeDefaults = true
            prettyPrint = true
        }

    fun encode(cheats: List<Cheat>): String {
        return json.encodeToString(CheatsConfig.serializer(), CheatsConfig(cheats = cheats))
    }

    fun decode(text: String): List<Cheat> {
        val root =
            runCatching { json.parseToJsonElement(text).jsonObject }
                .getOrNull() ?: return emptyList()

        val entries = root.cheatsArray() ?: return emptyList()

        val cheats =
            entries.mapNotNull { element ->
                runCatching { json.decodeFromJsonElement(Cheat.serializer(), element) }
                    .getOrNull()
                    ?.takeIf { it.id.isNotBlank() }
            }

        return cheats.distinctBy { it.id }
    }

    private fun JsonObject.cheatsArray(): JsonArray? {
        return runCatching { this["cheats"]?.jsonArray }.getOrNull()
    }
}
