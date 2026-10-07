package com.jkteknologies.androidanalyzer.ui.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jkteknologies.androidanalyzer.data.InstalledAppReader
import com.jkteknologies.androidanalyzer.data.UsageAccessStatus
import com.jkteknologies.androidanalyzer.domain.AppCategoryFilter
import com.jkteknologies.androidanalyzer.domain.AppInventory
import com.jkteknologies.androidanalyzer.domain.FigureUiState
import com.jkteknologies.androidanalyzer.ui.home.ResultPoster
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Owns the Details screen's read cycle — the 003 refresh-cycle rules verbatim
 * applied to one inventory figure (004 research.md R-07, data-model §7):
 * presentation triggers reset to [FigureUiState.Loading], the user
 * [refresh] preserves settled values in place, repeated triggers coalesce
 * into the running pass, superseded posts are dropped by epoch, and a shut
 * down executor is replaced from the factory. All state is UI-thread
 * confined or main-thread posted.
 *
 * The holder additionally owns the pure filter selection ([setFilter],
 * [applyPendingFilter] never trigger a read — FR-005/FR-012) and the
 * usage-access flag posted by each pass (data-model §5).
 */
class DetailsStateHolder(
    private val installedAppReader: InstalledAppReader,
    private val usageAccessStatus: UsageAccessStatus,
    private val poster: ResultPoster,
    private val executorFactory: () -> ExecutorService = { Executors.newSingleThreadExecutor() },
) {
    private var executor: ExecutorService = executorFactory()

    /** Identity of the current read pass; posts carrying a stale one are dropped (003 C-5). */
    private var epoch: Int = 0

    /** True while a pass is queued or running — the coalescing guard. */
    private var cycleRunning: Boolean = false

    /** The screen's one figure (FR-010): the full sorted inventory. */
    var inventory: FigureUiState<AppInventory> by mutableStateOf(FigureUiState.Loading)
        private set

    /**
     * Usage-access grant state as of the latest landed pass (data-model §5):
     * `null` until the first pass lands, then `true`/`false`. `false` means
     * every `storageBytes` of the same pass is `null` (V-D6) and the grant
     * hint shows (FR-008).
     */
    var usageAccessGranted: Boolean? by mutableStateOf(null)
        private set

    /**
     * The list restriction (FR-005): starts `ALL`; survives tab switches
     * in-session; a Home arrival overrides it (FR-012). Backed by a private
     * mutable state so the explicit [setFilter] owns the only write path.
     */
    val filter: AppCategoryFilter get() = filterState.value

    private val filterState = mutableStateOf(AppCategoryFilter.ALL)

    /**
     * Refresh indication (003 C-4): true from a refresh trigger — started or
     * absorbed — until the pass's completion post applies, which is ordered
     * after the inventory post.
     */
    var isRefreshing: Boolean by mutableStateOf(false)
        private set

    /**
     * Presentation trigger (launch / in-app return / resume): reset the
     * inventory to [FigureUiState.Loading], then one background pass. A
     * shut-down executor is replaced from the factory first.
     */
    fun startReadCycle() {
        startCycle(resetOnStart = true)
    }

    /**
     * User refresh trigger (003 C-1/C-2): re-read as a fresh snapshot
     * **without resetting** — the current value stays on screen until the
     * post lands in place. A trigger arriving while a pass is already queued
     * or running is absorbed into it.
     */
    fun refresh() {
        if (cycleRunning) {
            isRefreshing = true // absorbed (C-2)
            return
        }
        isRefreshing = true
        startCycle(resetOnStart = false)
    }

    /** Pure selection change — recomposes the list only, no read (FR-005, V-D3). */
    fun setFilter(filter: AppCategoryFilter) {
        filterState.value = filter
    }

    /**
     * Consumes a Home arrival (FR-012): overrides whatever filter was chosen
     * before. The same plain write as [setFilter] — the separate name records
     * the navigation meaning (V-D4).
     */
    fun applyPendingFilter(filter: AppCategoryFilter) {
        filterState.value = filter
    }

    /**
     * One background pass: the usage-access check and the inventory read,
     * then the figure post and the completion post in FIFO order — the
     * indication clears only after the figure has landed (V-D5).
     */
    private fun startCycle(resetOnStart: Boolean) {
        if (resetOnStart) {
            inventory = FigureUiState.Loading
        }
        val cycleEpoch = ++epoch
        cycleRunning = true
        if (executor.isShutdown) {
            executor = executorFactory()
        }
        executor.execute {
            val granted = try {
                usageAccessStatus.granted()
            } catch (t: Throwable) {
                null // unknown — neither hint nor figures follow from a broken check
            }
            val state = try {
                installedAppReader.read()?.let { FigureUiState.Available(it) } ?: FigureUiState.Unavailable
            } catch (t: Throwable) {
                FigureUiState.Unavailable
            }
            poster.post {
                if (epoch == cycleEpoch) {
                    inventory = state
                    usageAccessGranted = granted
                }
            }
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
}
