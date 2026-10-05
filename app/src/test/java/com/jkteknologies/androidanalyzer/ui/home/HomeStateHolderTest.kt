package com.jkteknologies.androidanalyzer.ui.home

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
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Figure-state transitions with fake readers (task T013; V-1 runtime half,
 * FR-012/FR-014). No Android framework classes: the executor and poster are
 * injected fakes (R-14) — [ManualExecutor] holds the background pass until the
 * test runs it, and [ManualPoster] holds posted state writes until applied,
 * making the Loading → Available/Unavailable sequence observable step by step.
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
    }

    private val executor = ManualExecutor()
    private val poster = ManualPoster()

    private var memoryResult: MemoryReading? = MemoryReading.create(10_000, 4_000)
    private var storageResult: StorageReading? = StorageReading.create(8_000, 2_000)
    private var batteryResult: BatteryReading? = BatteryReading.create(87, charging = true)
    private var coreCountResult: CoreCount? = CoreCount(8)
    private var inventoryResult: ApplicationInventory? = ApplicationInventory.create(36, 121)

    private fun holder() = HomeStateHolder(
        memoryReader = MemoryReader { memoryResult },
        storageReader = StorageReader { storageResult },
        batteryReader = BatteryReader { batteryResult },
        coreCountReader = CoreCountReader { coreCountResult },
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
            coreCountReader = CoreCountReader { coreCountResult },
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
        coreCountResult = CoreCount.create(0)
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
            coreCountReader = CoreCountReader { coreCountResult },
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
}
