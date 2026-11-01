package com.swordfish.lemuroid.app.shared.game.viewmodel

import android.content.Context
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.cheats.CheatApplyMode
import com.swordfish.lemuroid.lib.cheats.CheatCompilationResult
import com.swordfish.lemuroid.lib.cheats.CheatCompiler
import com.swordfish.lemuroid.lib.cheats.CheatCompilers
import com.swordfish.lemuroid.lib.cheats.CheatsManager
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Keeps the cheats stored for the running game in sync with the libretro core.
 *
 * Cores expose two calls: `retro_cheat_reset` (drop everything) and `retro_cheat_set` (add one cheat).
 * Several cores, mGBA included, ignore the `enabled` flag of `retro_cheat_set`, so the only reliable way
 * to change the active set is to reset and re-add every enabled cheat. That is what [refresh] does,
 * atomically on the emulation thread.
 *
 * Cores whose compiler reports [CheatApplyMode.EVERY_FRAME] (melonDS) execute a code once per call and
 * forget it, so for them the enabled cheats are re-sent every few rendered frames instead.
 */
class GameViewModelCheats(
    private val appContext: Context,
    private val game: Game,
    private val systemCoreConfig: SystemCoreConfig,
    private val retroGameView: GameViewModelRetroGameView,
    private val cheatsManager: CheatsManager,
    private val sideEffects: GameViewModelSideEffects,
    private val scope: CoroutineScope,
) {
    private val compiler: CheatCompiler? = CheatCompilers.forCore(systemCoreConfig.coreID)
    private val mutex = Mutex()
    private var frameLoopJob: Job? = null

    /** Codes currently loaded in the core. Null until the first application. */
    private var appliedCodes: List<String>? = null

    fun isSupported(): Boolean {
        return systemCoreConfig.cheatsSupported && compiler != null
    }

    /**
     * Applies the stored cheats once the core rendered its first frame, which guarantees that
     * `retro_load_game` completed. Must run after the auto-save has been restored.
     */
    suspend fun applyInitialCheats() {
        if (!isSupported()) return

        try {
            retroGameView.waitGLEvent<GLRetroView.GLRetroEvents.FrameRendered>()
            refresh(force = true)
        } catch (e: Throwable) {
            Timber.e(e, "Error while applying initial cheats")
        }
    }

    /**
     * Re-reads the cheats of this game from disk and pushes the enabled ones to the core.
     * When [force] is false nothing happens if the enabled set did not change since the last call.
     */
    suspend fun refresh(force: Boolean = false) {
        val compiler = compiler ?: return
        if (!systemCoreConfig.cheatsSupported) return

        mutex.withLock {
            val cheats = cheatsManager.getCheats(game, systemCoreConfig.coreID)

            val codes = mutableListOf<String>()
            var invalidCheats = 0

            cheats.filter { it.enabled }.forEach { cheat ->
                when (val result = compiler.compile(cheat.code, cheat.format)) {
                    is CheatCompilationResult.Success -> codes.add(result.libretroCode)
                    is CheatCompilationResult.Failure -> {
                        invalidCheats++
                        Timber.w("Skipping cheat '${cheat.name}': ${result.error}")
                    }
                }
            }

            val previousCodes = appliedCodes
            if (!force && codes == previousCodes) return

            val view = retroGameView.retroGameView ?: return
            applyToCore(view, codes)
            appliedCodes = codes

            reportResult(codes.size, previousCodes?.size ?: 0, invalidCheats)
        }
    }

    private fun applyToCore(
        view: GLRetroView,
        codes: List<String>,
    ) {
        when (compiler?.applyMode) {
            CheatApplyMode.PERSISTENT -> applyOnce(view, codes)
            CheatApplyMode.EVERY_FRAME -> restartFrameLoop(view, codes)
            null -> Unit
        }
    }

    private fun applyOnce(
        view: GLRetroView,
        codes: List<String>,
    ) {
        // Queue on the emulation thread so that reset and set happen between two retro_run calls.
        view.queueEvent {
            try {
                LibretroDroid.resetCheat()
                codes.forEachIndexed { index, code ->
                    Timber.i("Applying cheat $index: $code")
                    LibretroDroid.setCheat(index, true, code)
                }
            } catch (e: Throwable) {
                Timber.e(e, "The core rejected the cheats")
                sideEffects.showToast(appContext.getString(R.string.game_toast_cheats_error))
            }
        }
    }

    /**
     * For cores that run a code only once per `retro_cheat_set`: re-send the enabled set every
     * [FRAME_LOOP_INTERVAL] rendered frames. Button-triggered codes need the button held for a moment.
     */
    private fun restartFrameLoop(
        view: GLRetroView,
        codes: List<String>,
    ) {
        frameLoopJob?.cancel()
        frameLoopJob = null

        if (codes.isEmpty()) return

        codes.forEachIndexed { index, code -> Timber.i("Scheduling cheat $index: ${code.trim()}") }

        frameLoopJob =
            scope.launch {
                var frame = 0
                view.getGLRetroEvents()
                    .filterIsInstance<GLRetroView.GLRetroEvents.FrameRendered>()
                    .collect {
                        if (frame++ % FRAME_LOOP_INTERVAL != 0) return@collect
                        view.queueEvent {
                            try {
                                codes.forEachIndexed { index, code ->
                                    LibretroDroid.setCheat(index, true, code)
                                }
                            } catch (e: Throwable) {
                                Timber.e(e, "The core rejected the cheats")
                            }
                        }
                    }
            }
    }

    private fun reportResult(
        applied: Int,
        previouslyApplied: Int,
        invalid: Int,
    ) {
        val resources = appContext.resources

        if (invalid > 0) {
            sideEffects.showToast(resources.getQuantityString(R.plurals.game_toast_cheats_invalid, invalid, invalid))
        }

        if (applied > 0) {
            sideEffects.showToast(resources.getQuantityString(R.plurals.game_toast_cheats_active, applied, applied))
        } else if (previouslyApplied > 0) {
            sideEffects.showToast(appContext.getString(R.string.game_toast_cheats_disabled))
        }
    }

    companion object {
        private const val FRAME_LOOP_INTERVAL = 4
    }
}
