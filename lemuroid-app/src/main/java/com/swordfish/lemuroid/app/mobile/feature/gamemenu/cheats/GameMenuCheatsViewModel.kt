package com.swordfish.lemuroid.app.mobile.feature.gamemenu.cheats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.lib.cheats.Cheat
import com.swordfish.lemuroid.lib.cheats.CheatCompilationResult
import com.swordfish.lemuroid.lib.cheats.CheatCompiler
import com.swordfish.lemuroid.lib.cheats.CheatCompilers
import com.swordfish.lemuroid.lib.cheats.CheatError
import com.swordfish.lemuroid.lib.cheats.CheatFormat
import com.swordfish.lemuroid.lib.cheats.CheatsManager
import com.swordfish.lemuroid.lib.cheats.presets.CheatPreset
import com.swordfish.lemuroid.lib.cheats.presets.CheatPresets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GameMenuCheatsViewModel(
    private val gameMenuRequest: GameMenuActivity.GameMenuRequest,
    private val cheatsManager: CheatsManager,
) : ViewModel() {
    class Factory(
        private val gameMenuRequest: GameMenuActivity.GameMenuRequest,
        private val cheatsManager: CheatsManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameMenuCheatsViewModel(gameMenuRequest, cheatsManager) as T
        }
    }

    data class Entry(
        val cheat: Cheat,
        val error: CheatError?,
    )

    data class PresetEntry(
        val preset: CheatPreset,
        val alreadyAdded: Boolean,
    )

    data class State(
        val loaded: Boolean = false,
        val entries: List<Entry> = emptyList(),
        val presets: List<PresetEntry> = emptyList(),
    )

    private val game = gameMenuRequest.game
    private val coreID = gameMenuRequest.coreConfig.coreID
    private val compiler: CheatCompiler? = CheatCompilers.forCore(coreID)

    private val cheats = MutableStateFlow<List<Cheat>>(emptyList())
    private val loaded = MutableStateFlow(false)
    private val persistMutex = Mutex()

    private val presets: List<CheatPreset> =
        if (compiler != null) {
            CheatPresets.forGame(game.systemId, game.title, game.fileName)
        } else {
            emptyList()
        }

    val uiState: Flow<State> =
        combine(cheats, loaded) { list, isLoaded ->
            val codes = list.map { it.code }.toSet()
            State(
                loaded = isLoaded,
                entries = list.map { Entry(it, validationError(it)) },
                presets = presets.map { PresetEntry(it, it.code in codes) },
            )
        }

    val supportedFormats: List<CheatFormat>
        get() = compiler?.supportedFormats ?: listOf(CheatFormat.AUTO)

    init {
        viewModelScope.launch {
            cheats.value = cheatsManager.getCheats(game, coreID)
            loaded.value = true
        }
    }

    fun validate(
        code: String,
        format: CheatFormat,
    ): CheatCompilationResult {
        return compiler?.compile(code, format) ?: CheatCompilationResult.Failure(CheatError.Empty)
    }

    fun save(cheat: Cheat) {
        update { list ->
            if (list.any { it.id == cheat.id }) {
                list.map { if (it.id == cheat.id) cheat else it }
            } else {
                list + cheat
            }
        }
    }

    /** Adds the given presets as enabled cheats, skipping the ones already present. */
    fun addPresets(selected: List<CheatPreset>) {
        update { list ->
            val codes = list.map { it.code }.toSet()
            list +
                selected
                    .filter { it.code !in codes }
                    .map { Cheat(name = it.name, code = it.code, format = it.format, enabled = true) }
        }
    }

    fun delete(id: String) {
        update { list -> list.filterNot { it.id == id } }
    }

    fun setEnabled(
        id: String,
        enabled: Boolean,
    ) {
        update { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }
    }

    private fun update(transform: (List<Cheat>) -> List<Cheat>) {
        // Never write before the stored list has been read, or we would overwrite it with a partial one.
        if (!loaded.value) return

        cheats.value = transform(cheats.value)

        viewModelScope.launch {
            persistMutex.withLock {
                cheatsManager.setCheats(game, coreID, cheats.value)
            }
        }
    }

    private fun validationError(cheat: Cheat): CheatError? {
        return (validate(cheat.code, cheat.format) as? CheatCompilationResult.Failure)?.error
    }
}
