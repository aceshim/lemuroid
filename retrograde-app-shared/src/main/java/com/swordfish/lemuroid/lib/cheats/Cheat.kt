package com.swordfish.lemuroid.lib.cheats

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * The code formats a user can pick when entering a cheat.
 *
 * The format is used to validate and canonicalize the text the user typed. Cores that
 * auto-detect formats (mGBA does) still receive the canonical line and detect it themselves.
 */
@Serializable
enum class CheatFormat {
    /** Detect the format of every line from its shape. */
    AUTO,

    /** GameShark / Action Replay style: 8 + 8 hex digits per line. */
    GAMESHARK,

    /** CodeBreaker style: 8 + 4 hex digits per line. */
    CODEBREAKER,

    /** Raw memory patch: `AAAAAAAA:VV`, `AAAAAAAA:VVVV` or `AAAAAAAA:VVVVVVVV`. */
    RAW,

    /** Action Replay DS style: 8 + 8 hex digits per line (Nintendo DS). */
    ACTION_REPLAY_DS,
}

/** A single cheat as entered by the user. One cheat can span multiple code lines. */
@Serializable
data class Cheat(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val code: String = "",
    val format: CheatFormat = CheatFormat.AUTO,
    val enabled: Boolean = false,
)
