package com.swordfish.lemuroid.lib.cheats

import com.swordfish.lemuroid.common.kotlin.readTextAtomic
import com.swordfish.lemuroid.common.kotlin.runCatchingWithRetry
import com.swordfish.lemuroid.common.kotlin.writeTextAtomic
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Stores the cheats of every game as one JSON file per game and core, next to the save states.
 *
 * Files live in `<external files>/cheats/<core>/<rom file name>.cheats.json`. They are written
 * atomically and never touch save files or save states.
 */
class CheatsManager(private val directoriesManager: DirectoriesManager) {
    suspend fun getCheats(
        game: Game,
        coreID: CoreID,
    ): List<Cheat> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = getCheatsFile(game, coreID)
                if (file.exists()) {
                    CheatsSerializer.decode(file.readTextAtomic())
                } else {
                    emptyList()
                }
            }.getOrElse {
                Timber.e(it, "Unable to read cheats for ${game.fileName}")
                emptyList()
            }
        }

    suspend fun setCheats(
        game: Game,
        coreID: CoreID,
        cheats: List<Cheat>,
    ): Boolean =
        withContext(Dispatchers.IO) {
            runCatchingWithRetry(FILE_ACCESS_RETRIES) {
                getCheatsFile(game, coreID).writeTextAtomic(CheatsSerializer.encode(cheats))
            }.onFailure {
                Timber.e(it, "Unable to write cheats for ${game.fileName}")
            }.isSuccess
        }

    private fun getCheatsFile(
        game: Game,
        coreID: CoreID,
    ): File {
        val coreDirectory = File(directoriesManager.getCheatsDirectory(), coreID.coreName)
        coreDirectory.mkdirs()
        return File(coreDirectory, "${game.fileName}.cheats.json")
    }

    companion object {
        private const val FILE_ACCESS_RETRIES = 3
    }
}
