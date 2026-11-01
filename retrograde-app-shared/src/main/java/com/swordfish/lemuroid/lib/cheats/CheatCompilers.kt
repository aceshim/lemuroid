package com.swordfish.lemuroid.lib.cheats

import com.swordfish.lemuroid.lib.library.CoreID

/**
 * Registry of the cores that have a [CheatCompiler].
 *
 * To add cheat support for another core: write a compiler that matches that core's `retro_cheat_set`
 * parser, register it here and set `cheatsSupported = true` on the matching SystemCoreConfig.
 */
object CheatCompilers {
    fun forCore(coreID: CoreID): CheatCompiler? {
        return when (coreID) {
            CoreID.MGBA -> GbaCheatCompiler
            else -> null
        }
    }
}
