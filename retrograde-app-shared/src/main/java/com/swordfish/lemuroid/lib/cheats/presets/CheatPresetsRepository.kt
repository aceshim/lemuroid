package com.swordfish.lemuroid.lib.cheats.presets

import com.swordfish.lemuroid.common.kotlin.readTextAtomic
import com.swordfish.lemuroid.common.kotlin.writeTextAtomic
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Serves the cheat catalog: the copy last downloaded from [catalogUrl] if there is one, else the copy
 * bundled with the app. [refresh] downloads the file again; a failed or empty download never replaces
 * a working catalog.
 */
class CheatPresetsRepository(
    private val directoriesManager: DirectoriesManager,
    private val catalogUrl: String = DEFAULT_CATALOG_URL,
) {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    suspend fun getPresets(): List<CheatPreset> =
        withContext(Dispatchers.IO) {
            val cached =
                runCatching {
                    val file = getCacheFile()
                    if (file.exists()) CheatPresetsSerializer.decode(file.readTextAtomic()) else emptyList()
                }.getOrElse {
                    Timber.e(it, "Unable to read the cached cheat catalog")
                    emptyList()
                }

            cached.ifEmpty { CheatPresets.bundled }
        }

    /** Downloads the catalog. Returns true when a non-empty catalog was stored. */
    suspend fun refresh(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(catalogUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("Cheat catalog download failed: HTTP ${response.code}")
                        return@withContext false
                    }
                    val text = response.body?.string().orEmpty()
                    if (CheatPresetsSerializer.decode(text).isEmpty()) {
                        Timber.w("Downloaded cheat catalog is empty or invalid, keeping the current one")
                        return@withContext false
                    }
                    getCacheFile().writeTextAtomic(text)
                    true
                }
            }.getOrElse {
                Timber.w(it, "Unable to download the cheat catalog")
                false
            }
        }

    private fun getCacheFile(): File {
        return File(directoriesManager.getCheatsDirectory(), CACHE_FILE_NAME)
    }

    companion object {
        private const val CACHE_FILE_NAME = "presets-catalog.json"

        /** The bundled file, served from the project's GitHub repository. Edit it there to update the list. */
        const val DEFAULT_CATALOG_URL =
            "https://raw.githubusercontent.com/aceshim/lemuroid/ccr-43f3af2e-4wd0q3/" +
                "retrograde-app-shared/src/main/resources/cheats/presets.json"
    }
}
