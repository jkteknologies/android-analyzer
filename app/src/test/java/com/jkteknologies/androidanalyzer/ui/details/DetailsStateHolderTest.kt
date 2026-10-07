package com.jkteknologies.androidanalyzer.ui.details

import com.jkteknologies.androidanalyzer.data.InstalledAppReader
import com.jkteknologies.androidanalyzer.data.ShizukuAccessStatus
import com.jkteknologies.androidanalyzer.data.ShizukuAuthorizer
import com.jkteknologies.androidanalyzer.data.ShizukuChangeSource
import com.jkteknologies.androidanalyzer.data.UsageAccessStatus
import com.jkteknologies.androidanalyzer.domain.AppCategoryFilter
import com.jkteknologies.androidanalyzer.domain.AppClassification
import com.jkteknologies.androidanalyzer.domain.AppInventory
import com.jkteknologies.androidanalyzer.domain.FigureUiState
import com.jkteknologies.androidanalyzer.domain.InstalledApp
import com.jkteknologies.androidanalyzer.domain.ShizukuAccessState
import com.jkteknologies.androidanalyzer.ui.home.ResultPoster
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 004 details-holder contract through the established fakes seam
 * (data-model §9, V-D1..V-D6): the 003 cycle rules applied to the one
 * inventory figure, reader null/throw → Unavailable with recovery, pure
 * filter selection, the pending-filter override, and the usage-access
 * coupling. [ManualExecutor] and [ManualPoster] mirror the 003 shapes — no
 * Android framework classes on the test path.
 */
class DetailsStateHolderTest {

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

        /** Applies exactly the oldest posted action (V-D5 ordering observation). */
        fun applyNext() {
            actions.removeAt(0)()
        }
    }

    private val executor = ManualExecutor()
    private val poster = ManualPoster()

    private var grantedResult: Boolean = true
    private var readerCalls = 0
    private var inventoryResult: AppInventory? = inventoryOf(null, null)
    private var shizukuStateResult: ShizukuAccessState = ShizukuAccessState.NOT_INSTALLED
    private var authorizerCalls = 0
    private var changeUnsubscribed = false
    private val changeEvents = mutableListOf<() -> Unit>()

    private val holder = DetailsStateHolder(
        installedAppReader = InstalledAppReader { readerCalls++; inventoryResult },
        usageAccessStatus = UsageAccessStatus { grantedResult },
        shizukuAccessStatus = ShizukuAccessStatus { shizukuStateResult },
        shizukuAuthorizer = ShizukuAuthorizer { authorizerCalls++ },
        shizukuChangeSource = ShizukuChangeSource { onChange ->
            changeEvents.clear() // a new subscription replaces the removed one, like the real listener set
            changeEvents += onChange
            { changeUnsubscribed = true }
        },
        poster = poster,
        executorFactory = { executor },
    )

    private fun app(pkg: String, storageBytes: Long?, memoryBytes: Long? = null): InstalledApp =
        InstalledApp.create(pkg, "App $pkg", AppClassification.USER, storageBytes, memoryBytes)!!

    private fun inventoryOf(vararg storageBytes: Long?): AppInventory =
        AppInventory.create(storageBytes.mapIndexed { i, bytes -> app("p$i", bytes) })!!

    private fun landedApps(): List<InstalledApp> =
        (holder.inventory as FigureUiState.Available).value.apps

    @Test
    fun `presentation cycle resets the inventory to Loading before the read lands`() { // V-D1
        holder.startReadCycle()
        assertEquals(FigureUiState.Loading, holder.inventory)

        executor.runLastPass() // read happens, result only queued on the poster
        assertEquals(FigureUiState.Loading, holder.inventory)

        poster.applyAll()
        assertEquals(FigureUiState.Available(inventoryResult), holder.inventory)
    }

    @Test
    fun `refresh keeps the settled inventory in place and updates it when the post lands`() { // V-D1
        holder.startReadCycle()
        executor.runLastPass()
        poster.applyAll()
        val first = inventoryResult

        inventoryResult = inventoryOf(1, 2)
        holder.refresh()
        assertEquals(FigureUiState.Available(first), holder.inventory) // no reset — no placeholder flash

        executor.runLastPass()
        assertEquals(FigureUiState.Available(first), holder.inventory)

        poster.applyAll()
        assertEquals(FigureUiState.Available(inventoryResult), holder.inventory)
    }

    @Test
    fun `a reader returning null maps to Unavailable and a later pass recovers`() { // V-D2
        inventoryResult = null
        holder.startReadCycle()
        executor.runLastPass()
        poster.applyAll()
        assertEquals(FigureUiState.Unavailable, holder.inventory)

        inventoryResult = inventoryOf(5)
        holder.refresh()
        assertEquals(FigureUiState.Unavailable, holder.inventory) // kept until the post lands
        executor.runLastPass()
        poster.applyAll()
        assertEquals(FigureUiState.Available(inventoryResult), holder.inventory)
    }

    @Test
    fun `a reader throwing maps to Unavailable without crashing the pass`() { // V-D2
        val throwing = DetailsStateHolder(
            installedAppReader = InstalledAppReader { error("platform read failed") },
            usageAccessStatus = UsageAccessStatus { true },
            shizukuAccessStatus = ShizukuAccessStatus { shizukuStateResult },
            shizukuAuthorizer = ShizukuAuthorizer { authorizerCalls++ },
            shizukuChangeSource = ShizukuChangeSource { _ -> {} },
            poster = poster,
            executorFactory = { executor },
        )
        throwing.startReadCycle()
        executor.runLastPass()
        poster.applyAll()

        assertEquals(FigureUiState.Unavailable, throwing.inventory)
    }

    @Test
    fun `setFilter changes selection only with zero reader calls`() { // V-D3
        holder.setFilter(AppCategoryFilter.USER)
        assertEquals(AppCategoryFilter.USER, holder.filter)
        assertEquals(0, readerCalls)
    }

    @Test
    fun `applyPendingFilter overrides a previously chosen filter without reading`() { // V-D4
        holder.setFilter(AppCategoryFilter.USER)

        holder.applyPendingFilter(AppCategoryFilter.SYSTEM)

        assertEquals(AppCategoryFilter.SYSTEM, holder.filter)
        assertEquals(0, readerCalls)
    }

    @Test
    fun `refresh while a pass is running is absorbed not queued`() { // V-D5
        holder.startReadCycle() // pass queued — a cycle is "running"
        holder.refresh() // absorbed: forces the indicator, starts nothing
        assertEquals(1, executor.passes.size)
        assertTrue(holder.isRefreshing)
        holder.refresh() // a second absorbed trigger changes nothing
        assertEquals(1, executor.passes.size)

        executor.runLastPass()
        poster.applyAll()
        assertFalse(holder.isRefreshing) // cleared by the single pass's completion
    }

    @Test
    fun `posts from a superseded pass are dropped`() { // V-D5
        val first = inventoryResult
        holder.startReadCycle() // pass A queued (epoch 1)
        holder.startReadCycle() // pass B queued (epoch 2) — supersedes A
        executor.runLastPass() // run B
        poster.applyAll()

        inventoryResult = inventoryOf(9)
        executor.passes.first().run() // stale pass A runs late
        poster.applyAll() // its posts carry the old epoch — inert

        assertEquals(FigureUiState.Available(first), holder.inventory)
    }

    @Test
    fun `a refresh after shutdown draws a fresh executor and completes`() { // V-D5
        val first = ManualExecutor()
        val second = ManualExecutor()
        val executors = ArrayDeque(listOf(first, second))
        val revived = DetailsStateHolder(
            installedAppReader = InstalledAppReader { inventoryResult },
            usageAccessStatus = UsageAccessStatus { true },
            shizukuAccessStatus = ShizukuAccessStatus { shizukuStateResult },
            shizukuAuthorizer = ShizukuAuthorizer { authorizerCalls++ },
            shizukuChangeSource = ShizukuChangeSource { _ -> {} },
            poster = poster,
            executorFactory = { executors.removeFirst() },
        )
        revived.startReadCycle()
        first.runLastPass()
        poster.applyAll()
        revived.shutdown()
        revived.refresh() // must not hit the terminated pool

        assertEquals(1, second.passes.size)
        second.runLastPass()
        poster.applyAll()
        assertEquals(FigureUiState.Available(inventoryResult), revived.inventory)
        assertFalse(revived.isRefreshing)
    }

    @Test
    fun `isRefreshing clears only after the inventory post is applied`() { // V-D5
        holder.refresh()
        assertTrue(holder.isRefreshing)

        executor.runLastPass() // figure post + completion post, in order
        poster.applyNext()
        assertEquals(FigureUiState.Available(inventoryResult), holder.inventory)
        assertTrue(holder.isRefreshing) // completion not applied yet
        poster.applyNext()
        assertFalse(holder.isRefreshing)
    }

    @Test
    fun `usage access is unknown until the first pass lands`() { // V-D6
        assertNull(holder.usageAccessGranted)
    }

    @Test
    fun `a pass reporting usage access not granted carries null storageBytes for every app`() { // V-D6
        grantedResult = false
        inventoryResult = inventoryOf(null, null)
        holder.startReadCycle()
        executor.runLastPass()
        poster.applyAll()

        assertEquals(false, holder.usageAccessGranted)
        assertTrue(landedApps().isNotEmpty())
        assertTrue(landedApps().all { it.storageBytes == null })
    }

    @Test
    fun `a pass reporting usage access granted posts the flag and carries storage values`() { // V-D6
        grantedResult = true
        inventoryResult = inventoryOf(10, 20)
        holder.startReadCycle()
        executor.runLastPass()
        poster.applyAll()

        assertEquals(true, holder.usageAccessGranted)
        assertEquals(listOf(10L, 20L), landedApps().mapNotNull { it.storageBytes })
    }

    // V-S3 (005): the Shizuku state posts in the same pass as the figures —
    // the guidance and the memory figures cannot disagree. The state↔snapshot
    // coupling itself is the reader-side `if` (Android-only, quickstart M-2);
    // here the fakes pin the posted-pair contract the UI renders.

    @Test
    fun `shizuku state is unknown until the first pass lands`() { // V-S3
        assertNull(holder.shizukuAccess)
    }

    @Test
    fun `an authorized pass posts the state and carries memory values with zeros for absent packages`() { // V-S3
        shizukuStateResult = ShizukuAccessState.AUTHORIZED
        inventoryResult = AppInventory.create(
            listOf(app("p0", null, 111L), app("p1", null, 0L), app("p2", null, null)),
        )!!
        holder.startReadCycle()
        executor.runLastPass()
        poster.applyAll()

        assertEquals(ShizukuAccessState.AUTHORIZED, holder.shizukuAccess)
        assertEquals(listOf<Long?>(111L, 0L, null), landedApps().map { it.memoryBytes })
    }

    @Test
    fun `every non-authorized state posts itself and carries null memory for every app`() { // V-S3
        ShizukuAccessState.entries
            .filter { it != ShizukuAccessState.AUTHORIZED }
            .forEach { state ->
                shizukuStateResult = state
                inventoryResult = AppInventory.create(listOf(app("p0", null)))!!
                holder.startReadCycle()
                executor.runLastPass()
                poster.applyAll()

                assertEquals(state, holder.shizukuAccess)
                assertTrue(landedApps().all { it.memoryBytes == null })
            }
    }

    // V-S6 (005): the Allow action delegates to the authorizer seam and
    // survives one that throws (binder died between check and tap).

    @Test
    fun `requestAuthorization delegates to the authorizer seam`() { // V-S6
        holder.requestAuthorization()

        assertEquals(1, authorizerCalls)
    }

    @Test
    fun `requestAuthorization survives a throwing authorizer`() { // V-S6
        val throwing = DetailsStateHolder(
            installedAppReader = InstalledAppReader { inventoryResult },
            usageAccessStatus = UsageAccessStatus { true },
            shizukuAccessStatus = ShizukuAccessStatus { shizukuStateResult },
            shizukuAuthorizer = ShizukuAuthorizer { error("binder died") },
            shizukuChangeSource = ShizukuChangeSource { _ -> {} },
            poster = poster,
            executorFactory = { executor },
        )

        throwing.requestAuthorization() // must not throw
    }

    // V-S4/V-S5 (005): the change-source reactivity — one coalescing refresh
    // per event once a state has been posted, nothing before that, and the
    // subscription dies with shutdown().

    @Test
    fun `the initial sticky delivery triggers no refresh`() { // V-S5
        holder.startReadCycle() // subscribes + queues the first pass
        val passesAfterStart = executor.passes.size

        changeEvents.forEach { it() } // the sticky delivery of the fresh subscription
        poster.applyAll()

        assertEquals(passesAfterStart, executor.passes.size) // no second pass
        assertEquals(0, readerCalls) // nothing has run yet — no double pass at startup
    }

    @Test
    fun `a change event after a posted state triggers one coalescing refresh that re-reads by itself`() { // V-S4
        shizukuStateResult = ShizukuAccessState.AWAITING_AUTHORIZATION
        holder.startReadCycle()
        executor.runLastPass()
        poster.applyAll()
        assertEquals(ShizukuAccessState.AWAITING_AUTHORIZATION, holder.shizukuAccess)
        val callsAfterFirstPass = readerCalls

        shizukuStateResult = ShizukuAccessState.AUTHORIZED // the event was a grant
        changeEvents.forEach { it() }
        poster.applyAll() // onShizukuChanged runs on the main thread

        val passesAfterEvent = executor.passes.size
        assertEquals(callsAfterFirstPass, readerCalls) // the pass is queued, not yet run
        assertTrue(passesAfterEvent > 0)

        changeEvents.forEach { it() } // an event while that pass is queued: absorbed
        poster.applyAll()
        assertEquals(passesAfterEvent, executor.passes.size)

        executor.runLastPass() // the refresh pass runs
        poster.applyAll()
        assertEquals(callsAfterFirstPass + 1, readerCalls)
        assertEquals(ShizukuAccessState.AUTHORIZED, holder.shizukuAccess) // re-read landed with no gesture
    }

    @Test
    fun `shutdown unsubscribes the change source`() { // V-S4's dispose half
        holder.startReadCycle() // subscribes first — the T019 lazy lifecycle
        holder.shutdown()

        assertTrue(changeUnsubscribed)
    }

    @Test
    fun `change events trigger a refresh again after shutdown and a later revival`() { // T019
        shizukuStateResult = ShizukuAccessState.AWAITING_AUTHORIZATION
        holder.startReadCycle()
        executor.runLastPass()
        poster.applyAll()
        assertEquals(ShizukuAccessState.AWAITING_AUTHORIZATION, holder.shizukuAccess)
        holder.shutdown() // Details disposed: subscription removed, executor stopped
        assertTrue(changeUnsubscribed)

        shizukuStateResult = ShizukuAccessState.AUTHORIZED
        holder.refresh() // a later Details visit: fresh executor, fresh subscription
        changeEvents.forEach { it() } // a grant event on the revived subscription
        poster.applyAll()

        executor.runLastPass()
        poster.applyAll()

        // The auto re-read landed by itself — no restart, no manual gesture (FR-007).
        assertEquals(ShizukuAccessState.AUTHORIZED, holder.shizukuAccess)
    }
}
