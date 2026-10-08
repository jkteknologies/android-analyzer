package com.jkteknologies.androidanalyzer.ui.home

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jkteknologies.androidanalyzer.data.ApplicationCounter
import com.jkteknologies.androidanalyzer.data.BatteryReader
import com.jkteknologies.androidanalyzer.data.CoreTierReader
import com.jkteknologies.androidanalyzer.data.MemoryReader
import com.jkteknologies.androidanalyzer.data.StorageReader
import com.jkteknologies.androidanalyzer.domain.ApplicationInventory
import com.jkteknologies.androidanalyzer.domain.BatteryReading
import com.jkteknologies.androidanalyzer.domain.CoreTiers
import com.jkteknologies.androidanalyzer.domain.FigureUiState
import com.jkteknologies.androidanalyzer.domain.MemoryReading
import com.jkteknologies.androidanalyzer.domain.StorageReading
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Delivers a result action onto the UI thread. Production wraps the main
 * [Handler] (R-09); JVM tests inject a synchronous or manual fake — Android's
 * Handler/Looper classes must stay out of the test path (R-14).
 */
fun interface ResultPoster {
    fun post(action: () -> Unit)
}

/**
 * Owns the home screen's read cycles (task T013, research.md R-09/R-10).
 *
 * A cycle resets every figure to [FigureUiState.Loading] synchronously, then
 * performs one background pass over the five one-shot readers on a single
 * executor thread, posting each figure's result independently as it lands —
 * a reader that returns `null` or throws maps its figure (and only that
 * figure) to [FigureUiState.Unavailable] (FR-012/FR-014).
 *
 * Cycles are triggered by the hosting screen on entering composition while the
 * activity is already resumed (in-app return) and on lifecycle `ON_RESUME`
 * (launch and background resume) — FR-011, R-10. [shutdown] releases the
 * executor whenever the holder's screen leaves composition; the holder itself
 * survives destination switches, so the first cycle after such a shutdown (the
 * in-app return) draws a fresh executor from the factory — a terminated pool
 * must never be reused.
 *
 * Feature 003 adds the user refresh trigger [refresh] over the same single
 * pass (contracts/refresh-interaction.md C-1..C-8): it preserves current
 * figure states — no placeholder flash (FR-004) — coalesces repeated triggers
 * into the running pass (FR-006), exposes [isRefreshing] for the pull
 * indication (FR-003), and stamps every pass with an epoch so posts from a
 * superseded pass are dropped (FR-011, R-04). All state is UI-thread-confined
 * or main-thread posted — no synchronization primitives (C-1).
 */
class HomeStateHolder(
    private val memoryReader: MemoryReader,
    private val storageReader: StorageReader,
    private val batteryReader: BatteryReader,
    private val coreTierReader: CoreTierReader,
    private val applicationCounter: ApplicationCounter,
    private val poster: ResultPoster,
    private val executorFactory: () -> ExecutorService = { Executors.newSingleThreadExecutor() },
) {
    private var executor: ExecutorService = executorFactory()

    /**
     * Identity of the current read pass, bumped by every pass that actually
     * starts (C-5). Result and completion posts capture the epoch at pass
     * start and are dropped when a newer pass has superseded them (FR-011,
     * R-04).
     */
    private var epoch: Int = 0

    /**
     * True while a pass is queued or running — the coalescing guard. Written
     * only on the UI thread (trigger time) or via the completion post (C-1).
     */
    private var cycleRunning: Boolean = false

    var memory: FigureUiState<MemoryReading> by mutableStateOf(FigureUiState.Loading)
        private set
    var storage: FigureUiState<StorageReading> by mutableStateOf(FigureUiState.Loading)
        private set
    var battery: FigureUiState<BatteryReading> by mutableStateOf(FigureUiState.Loading)
        private set
    var processor: FigureUiState<CoreTiers> by mutableStateOf(FigureUiState.Loading)
        private set
    var applications: FigureUiState<ApplicationInventory> by mutableStateOf(FigureUiState.Loading)
        private set

    /**
     * Refresh indication (003 FR-003, C-4): true from a refresh trigger —
     * started or absorbed — until the running pass's completion post applies,
     * which is ordered after the pass's five figure posts.
     */
    var isRefreshing: Boolean by mutableStateOf(false)
        private set

    /**
     * Presentation trigger (launch / in-app return / resume — 002 FR-011,
     * unchanged): reset all figures to [FigureUiState.Loading], then one
     * background pass over the five reads. A shut-down executor is replaced
     * from the factory first — returning to HOME re-enters composition with
     * the same holder whose executor was released on dispose.
     */
    fun startReadCycle() {
        startCycle(resetOnStart = true)
    }

    /**
     * User refresh trigger (003 FR-001): re-read every figure as a fresh
     * snapshot **without resetting** — current values stay on screen until
     * each read's post lands in place (FR-004). A trigger arriving while a
     * pass is already queued or running is absorbed into it — never queued,
     * never parallel; the running pass's completion satisfies it (FR-006).
     */
    fun refresh() {
        if (cycleRunning) {
            isRefreshing = true // absorbed (C-2)
            return
        }
        isRefreshing = true
        startCycle(resetOnStart = false)
    }

    /**
     * One background pass over the five reads. Bumps the epoch so posts from
     * any earlier, superseded pass become inert (FR-011, R-04). The pass's
     * last step posts the cycle completion — after the five figure posts in
     * FIFO order, so the refresh indication clears only once the last figure
     * has landed (FR-003, R-L2).
     */
    private fun startCycle(resetOnStart: Boolean) {
        if (resetOnStart) {
            memory = FigureUiState.Loading
            storage = FigureUiState.Loading
            battery = FigureUiState.Loading
            processor = FigureUiState.Loading
            applications = FigureUiState.Loading
        }
        val cycleEpoch = ++epoch
        cycleRunning = true
        if (executor.isShutdown) {
            executor = executorFactory()
        }
        executor.execute {
            readAndPost(cycleEpoch, memoryReader::read) { memory = it }
            readAndPost(cycleEpoch, storageReader::read) { storage = it }
            readAndPost(cycleEpoch, batteryReader::read) { battery = it }
            readAndPost(cycleEpoch, coreTierReader::read) { processor = it }
            readAndPost(cycleEpoch, applicationCounter::count) { applications = it }
            poster.post {
                if (epoch == cycleEpoch) {
                    cycleRunning = false
                    isRefreshing = false
                }
            }
        }
    }

    /** Releases the single executor thread (host screen discarded). */
    fun shutdown() {
        executor.shutdown()
    }

    /**
     * One reader call on the executor thread, result posted to the UI thread —
     * applied only while [cycleEpoch] is still current (C-5). `null`, a thrown
     * exception, or a nonsensical reading (factory rejects it) all map to
     * [FigureUiState.Unavailable] — never an invented value. The catch of
     * [Throwable] is deliberate: FR-012's no-crash guarantee outranks the
     * generic-exception lint preference here.
     */
    private inline fun <T> readAndPost(
        cycleEpoch: Int,
        noinline read: () -> T?,
        crossinline set: (FigureUiState<T>) -> Unit,
    ) {
        val state = try {
            read()?.let { FigureUiState.Available(it) } ?: FigureUiState.Unavailable
        } catch (t: Throwable) {
            FigureUiState.Unavailable
        }
        poster.post { if (epoch == cycleEpoch) set(state) }
    }

    companion object {
        /** Production poster: main-thread [Handler] (R-09). */
        fun mainThreadPoster(): ResultPoster = ResultPoster { action ->
            Handler(Looper.getMainLooper()).post(action)
        }
    }
}
