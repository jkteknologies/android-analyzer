package com.jkteknologies.androidanalyzer.ui.home

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jkteknologies.androidanalyzer.data.ApplicationCounter
import com.jkteknologies.androidanalyzer.data.BatteryReader
import com.jkteknologies.androidanalyzer.data.CoreCountReader
import com.jkteknologies.androidanalyzer.data.MemoryReader
import com.jkteknologies.androidanalyzer.data.StorageReader
import com.jkteknologies.androidanalyzer.domain.ApplicationInventory
import com.jkteknologies.androidanalyzer.domain.BatteryReading
import com.jkteknologies.androidanalyzer.domain.CoreCount
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
 * executor when the holder's screen leaves composition for good.
 */
class HomeStateHolder(
    private val memoryReader: MemoryReader,
    private val storageReader: StorageReader,
    private val batteryReader: BatteryReader,
    private val coreCountReader: CoreCountReader,
    private val applicationCounter: ApplicationCounter,
    private val poster: ResultPoster,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) {
    var memory: FigureUiState<MemoryReading> by mutableStateOf(FigureUiState.Loading)
        private set
    var storage: FigureUiState<StorageReading> by mutableStateOf(FigureUiState.Loading)
        private set
    var battery: FigureUiState<BatteryReading> by mutableStateOf(FigureUiState.Loading)
        private set
    var processor: FigureUiState<CoreCount> by mutableStateOf(FigureUiState.Loading)
        private set
    var applications: FigureUiState<ApplicationInventory> by mutableStateOf(FigureUiState.Loading)
        private set

    /** Starts one read cycle: reset all figures, then one background pass over the five reads. */
    fun startReadCycle() {
        memory = FigureUiState.Loading
        storage = FigureUiState.Loading
        battery = FigureUiState.Loading
        processor = FigureUiState.Loading
        applications = FigureUiState.Loading
        executor.execute {
            readAndPost(memoryReader::read) { memory = it }
            readAndPost(storageReader::read) { storage = it }
            readAndPost(batteryReader::read) { battery = it }
            readAndPost(coreCountReader::read) { processor = it }
            readAndPost(applicationCounter::count) { applications = it }
        }
    }

    /** Releases the single executor thread (host screen discarded). */
    fun shutdown() {
        executor.shutdown()
    }

    /**
     * One reader call on the executor thread, result posted to the UI thread.
     * `null`, a thrown exception, or a nonsensical reading (factory rejects it)
     * all map to [FigureUiState.Unavailable] — never an invented value. The
     * catch of [Throwable] is deliberate: FR-012's no-crash guarantee outranks
     * the generic-exception lint preference here.
     */
    private inline fun <T> readAndPost(
        noinline read: () -> T?,
        crossinline set: (FigureUiState<T>) -> Unit,
    ) {
        val state = try {
            read()?.let { FigureUiState.Available(it) } ?: FigureUiState.Unavailable
        } catch (t: Throwable) {
            FigureUiState.Unavailable
        }
        poster.post { set(state) }
    }

    companion object {
        /** Production poster: main-thread [Handler] (R-09). */
        fun mainThreadPoster(): ResultPoster = ResultPoster { action ->
            Handler(Looper.getMainLooper()).post(action)
        }
    }
}
