package com.jkteknologies.androidanalyzer.ui.home

import com.jkteknologies.androidanalyzer.data.ApplicationCounter
import com.jkteknologies.androidanalyzer.data.BatteryReader
import com.jkteknologies.androidanalyzer.data.CoreTierReader
import com.jkteknologies.androidanalyzer.data.MemoryReader
import com.jkteknologies.androidanalyzer.data.StorageReader
import com.jkteknologies.androidanalyzer.domain.ApplicationInventory
import com.jkteknologies.androidanalyzer.domain.BatteryReading
import com.jkteknologies.androidanalyzer.domain.CoreTier
import com.jkteknologies.androidanalyzer.domain.CoreTiers
import com.jkteknologies.androidanalyzer.domain.FigureUiState
import com.jkteknologies.androidanalyzer.domain.MemoryReading
import com.jkteknologies.androidanalyzer.domain.StorageReading
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Figure-state transitions with fake readers (task T013; V-1 runtime half,
 * FR-012/FR-014) and the refresh contract of feature 003 (task T002;
 * contracts/refresh-interaction.md C-1..C-8, data-model.md §5 VR-1..VR-10).
 * No Android framework classes: the executor and poster are injected fakes
 * (R-14) — [ManualExecutor] holds the background pass until the test runs it,
 * and [ManualPoster] holds posted state writes until applied, making the
 * Loading → Available/Unavailable and idle → refreshing → idle sequences
 * observable step by step.
 */
class HomeStateHolderTest {

    /** Records the background pass instead of running it; the test decides when. */
    private class ManualExecutor : AbstractExecutorService() {
        val passes = mutableListOf<Runnable>()
        var shutDown = false

        override fun execute(command: Runnable) {
            passes += command
        }

        fun runLastPass() = passes.last().run()

        override fun shutdown() {
            shutDown = true
        }

        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown(): Boolean = shutDown
        override fun isTerminated(): Boolean = shutDown
        override fun awaitTermination(timeout: Long, unit: TimeUnit?): Boolean = shutDown
    }

    /** Records posted state writes instead of applying them; the test decides when. */
    private class ManualPoster : ResultPoster {
        val actions = mutableListOf<() -> Unit>()

        override fun post(action: () -> Unit) {
            actions += action
        }

        fun applyAll() {
            val pending = actions.toList()
            actions.clear()
            pending.forEach { it() }
        }

        /** Applies exactly the oldest posted action (VR-5 ordering observation). */
        fun applyNext() {
            actions.removeAt(0)()
        }
    }

    private val executor = ManualExecutor()
    private val poster = ManualPoster()

    private var memoryResult: MemoryReading? = MemoryReading.create(10_000, 4_000)
    private var storageResult: StorageReading? = StorageReading.create(8_000, 2_000)
    private var batteryResult: BatteryReading? = BatteryReading.create(87, charging = true)
    private var coreCountResult: CoreTiers? = CoreTiers.create(8, listOf(CoreTier(8, 1_800_000_000)))
    private var inventoryResult: ApplicationInventory? = ApplicationInventory.create(36, 121)

    private fun holder() = HomeStateHolder(
        memoryReader = MemoryReader { memoryResult },
        storageReader = StorageReader { storageResult },
        batteryReader = BatteryReader { batteryResult },
        coreTierReader = CoreTierReader { coreCountResult },
        applicationCounter = ApplicationCounter { inventoryResult },
        poster = poster,
        executorFactory = { executor },
    )

    @Test
    fun `cycle resets every figure to Loading before any read lands`() {
        val holder = holder()
        holder.startReadCycle()
        assertEquals(FigureUiState.Loading, holder.memory)
        assertEquals(FigureUiState.Loading, holder.storage)
        assertEquals(FigureUiState.Loading, holder.battery)
        assertEquals(FigureUiState.Loading, holder.processor)
        assertEquals(FigureUiState.Loading, holder.applications)

        executor.runLastPass() // reads happen, results only queued on the poster
        assertEquals(FigureUiState.Loading, holder.memory)

        poster.applyAll() // results land
        assertEquals(FigureUiState.Available(memoryResult), holder.memory)
        assertEquals(FigureUiState.Available(storageResult), holder.storage)
        assertEquals(FigureUiState.Available(batteryResult), holder.battery)
        assertEquals(FigureUiState.Available(coreCountResult), holder.processor)
        assertEquals(FigureUiState.Available(inventoryResult), holder.applications)
    }

    @Test
    fun `a reader returning null maps only its figure to Unavailable`() {
        memoryResult = null
        val holder = holder()
        holder.startReadCycle()
        executor.runLastPass()
        poster.applyAll()

        assertEquals(FigureUiState.Unavailable, holder.memory)
        assertEquals(FigureUiState.Available(storageResult), holder.storage)
        assertEquals(FigureUiState.Available(batteryResult), holder.battery)
        assertEquals(FigureUiState.Available(coreCountResult), holder.processor)
        assertEquals(FigureUiState.Available(inventoryResult), holder.applications)
    }

    @Test
    fun `a reader throwing maps its figure to Unavailable without crashing the pass`() {
        storageResult = null
        val holderWithThrowingStorage = HomeStateHolder(
            memoryReader = MemoryReader { memoryResult },
            storageReader = StorageReader { error("platform read failed") },
            batteryReader = BatteryReader { batteryResult },
            coreTierReader = CoreTierReader { coreCountResult },
            applicationCounter = ApplicationCounter { inventoryResult },
            poster = poster,
            executorFactory = { executor },
        )
        holderWithThrowingStorage.startReadCycle()
        executor.runLastPass()
        poster.applyAll()

        assertEquals(FigureUiState.Unavailable, holderWithThrowingStorage.storage)
        assertEquals(FigureUiState.Available(memoryResult), holderWithThrowingStorage.memory)
        assertEquals(FigureUiState.Available(inventoryResult), holderWithThrowingStorage.applications)
    }

    @Test
    fun `a nonsensical reading maps to Unavailable never clamped`() {
        // Factory rejects total <= 0 — the reader's mapping yields null → Unavailable.
        coreCountResult = CoreTiers.create(0, listOf(CoreTier(0, 1_800_000_000)))
        val holder = holder()
        holder.startReadCycle()
        executor.runLastPass()
        poster.applyAll()

        assertEquals(FigureUiState.Unavailable, holder.processor)
    }

    @Test
    fun `terminal states reset to Loading on the next cycle then take new values`() {
        val holder = holder()
        holder.startReadCycle()
        executor.runLastPass()
        poster.applyAll()
        assertEquals(FigureUiState.Available(memoryResult), holder.memory)

        val freshMemory = MemoryReading.create(10_000, 9_000)!!
        memoryResult = freshMemory
        batteryResult = null

        holder.startReadCycle()
        assertEquals(FigureUiState.Loading, holder.memory) // V-1: reset precedes the new read
        executor.runLastPass()
        poster.applyAll()
        assertEquals(FigureUiState.Available(freshMemory), holder.memory)
        assertEquals(FigureUiState.Unavailable, holder.battery)
    }

    @Test
    fun `each cycle submits exactly one background pass`() {
        val holder = holder()
        holder.startReadCycle()
        holder.startReadCycle()
        assertEquals(2, executor.passes.size)
    }

    @Test
    fun `shutdown releases the executor`() {
        val holder = holder()
        holder.shutdown()
        assertTrue(executor.shutDown)
    }

    @Test
    fun `a cycle after shutdown draws a fresh executor instead of the rejected pool`() {
        val first = ManualExecutor()
        val second = ManualExecutor()
        val executors = ArrayDeque(listOf(first, second))
        val holder = HomeStateHolder(
            memoryReader = MemoryReader { memoryResult },
            storageReader = StorageReader { storageResult },
            batteryReader = BatteryReader { batteryResult },
            coreTierReader = CoreTierReader { coreCountResult },
            applicationCounter = ApplicationCounter { inventoryResult },
            poster = poster,
            executorFactory = { executors.removeFirst() },
        )
        holder.startReadCycle()
        assertEquals(1, first.passes.size)

        holder.shutdown()
        holder.startReadCycle() // in-app return — RejectedExecutionException before the fix

        assertEquals(1, second.passes.size) // the revived cycle landed on the fresh executor
        assertEquals(1, first.passes.size) // the terminated pool stays untouched
        second.runLastPass()
        poster.applyAll()
        assertEquals(FigureUiState.Available(memoryResult), holder.memory)
    }

    // ------------------------------------------------------------------
    // Feature 003: refresh contract (VR-1..VR-10). A pass posts its five
    // figure results and then one completion post, in order; the poster
    // fakes make that ordering observable action by action.
    // ------------------------------------------------------------------

    @Test
    fun `refresh keeps settled states until reads land then updates in place`() { // VR-1, VR-2
        val firstMemory = memoryResult
        val holder = holder()
        holder.startReadCycle()
        executor.runLastPass()
        poster.applyAll()
        assertEquals(FigureUiState.Available(firstMemory), holder.memory)

        val freshMemory = MemoryReading.create(10_000, 9_000)!!
        memoryResult = freshMemory

        holder.refresh()
        // VR-1: no reset — the old value stays; no figure flashes to Loading.
        assertEquals(FigureUiState.Available(firstMemory), holder.memory)
        assertEquals(FigureUiState.Available(storageResult), holder.storage)

        executor.runLastPass() // reads complete; results only queued, not applied
        assertEquals(FigureUiState.Available(firstMemory), holder.memory)

        poster.applyAll() // VR-2: in-place update when the post lands
        assertEquals(FigureUiState.Available(freshMemory), holder.memory)
    }

    @Test
    fun `refresh re-reads every reader exactly once per started pass`() { // VR-2
        var memoryReads = 0
        var storageReads = 0
        var batteryReads = 0
        var coreReads = 0
        var appReads = 0
        val holder = HomeStateHolder(
            memoryReader = MemoryReader { memoryReads++; memoryResult },
            storageReader = StorageReader { storageReads++; storageResult },
            batteryReader = BatteryReader { batteryReads++; batteryResult },
            coreTierReader = CoreTierReader { coreReads++; coreCountResult },
            applicationCounter = ApplicationCounter { appReads++; inventoryResult },
            poster = poster,
            executorFactory = { executor },
        )
        holder.startReadCycle()
        executor.runLastPass()
        poster.applyAll()
        holder.refresh()
        executor.runLastPass()
        poster.applyAll()

        assertEquals(2, memoryReads)
        assertEquals(2, storageReads)
        assertEquals(2, batteryReads)
        assertEquals(2, coreReads)
        assertEquals(2, appReads)
    }

    @Test
    fun `refresh while a pass is running is absorbed not queued`() { // VR-3, VR-6
        val holder = holder()
        holder.startReadCycle() // pass queued, not yet run — a cycle is "running"
        holder.refresh() // absorbed: forces the indicator, starts nothing
        assertEquals(1, executor.passes.size)
        assertTrue(holder.isRefreshing)
        holder.refresh() // a second absorbed trigger changes nothing (VR-6)
        assertEquals(1, executor.passes.size)

        executor.runLastPass()
        poster.applyAll()
        assertFalse(holder.isRefreshing) // cleared by the single pass's completion
    }

    @Test
    fun `refresh during in-flight presentation pass settles figures once from that pass`() { // VR-4
        val holder = holder()
        holder.startReadCycle() // initial load: figures Loading, pass queued
        holder.refresh() // user pulls while placeholders show — absorbed
        assertEquals(1, executor.passes.size)

        executor.runLastPass()
        poster.applyAll()

        assertEquals(FigureUiState.Available(memoryResult), holder.memory)
        assertEquals(FigureUiState.Available(inventoryResult), holder.applications)
        assertFalse(holder.isRefreshing)
    }

    @Test
    fun `isRefreshing clears only after the last figure post is applied`() { // VR-5
        val holder = holder()
        holder.refresh()
        assertTrue(holder.isRefreshing)

        executor.runLastPass() // five figure posts + one completion post, in order
        repeat(5) { poster.applyNext() } // all figure results land…
        assertTrue(holder.isRefreshing) // …but the completion has not been applied yet
        poster.applyNext()
        assertFalse(holder.isRefreshing)
    }

    @Test
    fun `posts from a superseded pass are dropped`() { // VR-7
        val firstMemory = memoryResult
        val superseded = MemoryReading.create(10_000, 9_000)!!
        val holder = holder()
        holder.startReadCycle() // pass A queued (epoch 1)
        holder.startReadCycle() // pass B queued (epoch 2) — supersedes A
        executor.runLastPass() // run B: figures take the current values
        poster.applyAll()

        memoryResult = superseded
        executor.passes.first().run() // stale pass A runs late, reads superseded values
        poster.applyAll() // its posts carry the old epoch — inert

        assertEquals(FigureUiState.Available(firstMemory), holder.memory)
    }

    @Test
    fun `a superseded completion post cannot clear a live refresh indicator`() { // VR-7
        val holder = holder()
        holder.startReadCycle() // pass A queued (epoch 1) — held back, runs late
        holder.startReadCycle() // pass B queued (epoch 2) — supersedes A
        executor.runLastPass() // B runs and completes
        poster.applyAll()

        holder.refresh() // live refresh pass (epoch 3), indicator on
        assertTrue(holder.isRefreshing)
        executor.passes.first().run() // stale pass A finally runs: figures + completion
        poster.applyAll() // all epoch-1 posts, including the completion, are inert
        assertTrue(holder.isRefreshing) // only the live pass's completion may clear it
    }

    @Test
    fun `refresh after shutdown draws a fresh executor and completes`() { // VR-8
        val first = ManualExecutor()
        val second = ManualExecutor()
        val executors = ArrayDeque(listOf(first, second))
        val holder = HomeStateHolder(
            memoryReader = MemoryReader { memoryResult },
            storageReader = StorageReader { storageResult },
            batteryReader = BatteryReader { batteryResult },
            coreTierReader = CoreTierReader { coreCountResult },
            applicationCounter = ApplicationCounter { inventoryResult },
            poster = poster,
            executorFactory = { executors.removeFirst() },
        )
        holder.startReadCycle()
        first.runLastPass() // the in-app-return cycle completes before dispose
        poster.applyAll()
        holder.shutdown()
        holder.refresh() // must not hit the terminated pool

        assertEquals(1, second.passes.size)
        second.runLastPass()
        poster.applyAll()
        assertEquals(FigureUiState.Available(memoryResult), holder.memory)
        assertFalse(holder.isRefreshing)
    }

    @Test
    fun `refresh retries unavailable figures and can recover or stay unavailable`() { // VR-9
        memoryResult = null
        val holder = holder()
        holder.startReadCycle()
        executor.runLastPass()
        poster.applyAll()
        assertEquals(FigureUiState.Unavailable, holder.memory)

        memoryResult = MemoryReading.create(10_000, 1_000)
        holder.refresh()
        assertEquals(FigureUiState.Unavailable, holder.memory) // no reset before the post
        executor.runLastPass()
        poster.applyAll()
        assertEquals(FigureUiState.Available(memoryResult), holder.memory) // recovered

        memoryResult = null
        holder.refresh()
        executor.runLastPass()
        poster.applyAll()
        assertEquals(FigureUiState.Unavailable, holder.memory) // failed again — stays unavailable
    }

    @Test
    fun `presentation cycle mid-refresh resets to Loading and supersedes the refresh pass`() { // VR-10
        val holder = holder()
        holder.refresh() // refresh pass R queued, indicator on
        assertTrue(holder.isRefreshing)
        holder.startReadCycle() // ON_RESUME during the refresh: 002 behavior verbatim
        assertEquals(FigureUiState.Loading, holder.memory) // reset happened synchronously

        executor.runLastPass() // run the newest (presentation) pass
        poster.applyAll()
        assertEquals(FigureUiState.Available(memoryResult), holder.memory)
        assertFalse(holder.isRefreshing) // cleared by the newest pass's completion

        executor.passes.first().run() // the stale refresh pass R finishes late…
        poster.applyAll() // …its posts (figures and completion) are all inert
        assertEquals(FigureUiState.Available(memoryResult), holder.memory)
        assertFalse(holder.isRefreshing)
    }
}
