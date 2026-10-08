package com.jkteknologies.resourceradar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Data-model validation rules V-1..V-6 (specs/002-home-screen/data-model.md §7).
 *
 * Pure JVM — the domain layer must not import Android (plan.md Project Structure).
 * V-1's runtime half (any → Loading on cycle start; terminal Available/Unavailable
 * until the next cycle) is enforced by the home state holder and covered by
 * [com.jkteknologies.resourceradar.ui.home.HomeStateHolderTest]; here the
 * three-state surface itself is pinned.
 */
class FiguresTest {

    // ---------------------------------------------------------------- V-1

    @Test
    fun `figure state surface is exactly Loading, Available(value), Unavailable`() {
        val available: FigureUiState<CoreCount> = FigureUiState.Available(CoreCount(8))
        val labels = listOf(
            FigureUiState.Loading as FigureUiState<CoreCount>,
            available,
            FigureUiState.Unavailable,
        ).map { state ->
            // Exhaustive `when` (no `else`): adding a fourth state breaks this test at compile time.
            when (state) {
                is FigureUiState.Loading -> "loading"
                is FigureUiState.Available -> "available"
                FigureUiState.Unavailable -> "unavailable"
            }
        }
        assertEquals(listOf("loading", "available", "unavailable"), labels)
    }

    @Test
    fun `Available carries its value and is distinct from Loading and Unavailable`() {
        val value = MemoryReading.create(10L * 1024 * 1024 * 1024, 4L * 1024 * 1024 * 1024)!!
        val available = FigureUiState.Available(value)
        assertEquals(value, available.value)
        assertTrue(FigureUiState.Loading != available)
        assertTrue(FigureUiState.Unavailable != available)
    }

    // ---------------------------------------------------------------- V-2

    @Test
    fun `memory allocatedBytes equals total minus available from one reading`() {
        val reading = MemoryReading.create(totalBytes = 10_000, availableBytes = 4_000)!!
        assertEquals(6_000L, reading.allocatedBytes)
    }

    @Test
    fun `storage usedBytes equals total minus available from one reading`() {
        val reading = StorageReading.create(totalBytes = 10_000, availableBytes = 7_500)!!
        assertEquals(2_500L, reading.usedBytes)
    }

    // ---------------------------------------------------------------- V-3

    @Test
    fun `memory and storage byte figures stay non-negative and pair sums equal total`() {
        val memoryCases = listOf(0L to 10_000L, 4_000L to 10_000L, 10_000L to 10_000L)
        for ((available, total) in memoryCases) {
            val reading = MemoryReading.create(totalBytes = total, availableBytes = available)!!
            assertTrue(reading.availableBytes >= 0)
            assertTrue(reading.allocatedBytes >= 0)
            assertEquals(total, reading.availableBytes + reading.allocatedBytes)
        }
        val storageCases = listOf(0L to 8_000L, 1L to 8_000L, 8_000L to 8_000L)
        for ((available, total) in storageCases) {
            val reading = StorageReading.create(totalBytes = total, availableBytes = available)!!
            assertTrue(reading.availableBytes >= 0)
            assertTrue(reading.usedBytes >= 0)
            assertEquals(total, reading.availableBytes + reading.usedBytes)
        }
    }

    // ---------------------------------------------------------------- V-4

    @Test
    fun `memory rejects nonsensical readings with null instead of clamping`() {
        assertNull(MemoryReading.create(totalBytes = 0, availableBytes = 0))          // total must be > 0
        assertNull(MemoryReading.create(totalBytes = -1, availableBytes = 0))         // negative total
        assertNull(MemoryReading.create(totalBytes = 100, availableBytes = 101))      // available > total
        assertNull(MemoryReading.create(totalBytes = 100, availableBytes = -1))       // negative available
    }

    @Test
    fun `memory accepts the boundary readings`() {
        assertEquals(100L, MemoryReading.create(totalBytes = 100, availableBytes = 0)!!.allocatedBytes)
        assertEquals(0L, MemoryReading.create(totalBytes = 100, availableBytes = 100)!!.allocatedBytes)
    }

    @Test
    fun `storage rejects nonsensical readings with null instead of clamping`() {
        assertNull(StorageReading.create(totalBytes = 0, availableBytes = 0))
        assertNull(StorageReading.create(totalBytes = -5, availableBytes = 0))
        assertNull(StorageReading.create(totalBytes = 100, availableBytes = 101))
        assertNull(StorageReading.create(totalBytes = 100, availableBytes = -1))
    }

    // ---------------------------------------------------------------- V-5

    @Test
    fun `battery rejects levels outside the 0 to 100 range with null`() {
        assertNull(BatteryReading.create(levelPercent = -1, charging = false))
        assertNull(BatteryReading.create(levelPercent = 101, charging = true))
    }

    @Test
    fun `battery accepts boundary levels 0 and 100`() {
        assertEquals(0, BatteryReading.create(levelPercent = 0, charging = false)!!.levelPercent)
        assertEquals(100, BatteryReading.create(levelPercent = 100, charging = true)!!.levelPercent)
    }

    // ---------------------------------------------------------------- V-6

    @Test
    fun `core count rejects counts below one`() {
        assertNull(CoreCount.create(count = 0))
        assertNull(CoreCount.create(count = -3))
    }

    @Test
    fun `core count accepts one and above`() {
        assertEquals(1, CoreCount.create(count = 1)!!.count)
        assertEquals(8, CoreCount.create(count = 8)!!.count)
    }
}
