package com.jkteknologies.resourceradar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * V-S1/V-S2 (005 data-model §9): the aggregation rule and the snapshot → row
 * merge rule — every branch the Details memory figure can render.
 */
class ShizukuMemoryTest {

    // V-S1: aggregation

    @Test
    fun sumsAllProcessesOfOnePackage() {
        val snapshot = aggregateProcessMemory(
            listOf(
                ProcessMemory("com.foo", 100),
                ProcessMemory("com.foo:service", 23),
                ProcessMemory("com.foo:ipc_1234", 2),
            ),
        )
        assertEquals(mapOf("com.foo" to 125L), snapshot)
    }

    @Test
    fun keepsPackagesIndependent() {
        val snapshot = aggregateProcessMemory(
            listOf(
                ProcessMemory("com.foo", 100),
                ProcessMemory("com.bar", 7),
                ProcessMemory("com.bar:sync", 3),
            ),
        )
        assertEquals(mapOf("com.foo" to 100L, "com.bar" to 10L), snapshot)
    }

    @Test
    fun negativeEntryMapsItsPackageToNull() {
        val snapshot = aggregateProcessMemory(
            listOf(
                ProcessMemory("com.foo", 100),
                ProcessMemory("com.foo:bad", -1),
            ),
        )
        assertEquals(mapOf<String, Long?>("com.foo" to null), snapshot)
    }

    @Test
    fun negativeOnlyPackageMapsToNull() {
        val snapshot = aggregateProcessMemory(listOf(ProcessMemory("com.foo", -5)))
        assertEquals(mapOf<String, Long?>("com.foo" to null), snapshot)
    }

    @Test
    fun blankProcessNamesAreDropped() {
        val snapshot = aggregateProcessMemory(
            listOf(
                ProcessMemory("", 10),
                ProcessMemory(" ", 5),
                ProcessMemory("com.foo", 1),
            ),
        )
        assertEquals(mapOf("com.foo" to 1L), snapshot)
    }

    // V-S2: the merge rule

    @Test
    fun nullSnapshotIsNotAvailable() {
        assertNull(memoryBytesFor(null, "com.foo"))
    }

    @Test
    fun absentPackageIsATruthfulZero() {
        assertEquals(0L, memoryBytesFor(mapOf("com.bar" to 5L), "com.foo"))
    }

    @Test
    fun presentValueStands() {
        assertEquals(5L, memoryBytesFor(mapOf("com.foo" to 5L), "com.foo"))
    }

    @Test
    fun presentNullIsAPerAppFailure() {
        assertNull(memoryBytesFor(mapOf("com.foo" to null), "com.foo"))
    }

    @Test
    fun negativeValueFailsValidation() {
        assertNull(memoryBytesFor(mapOf("com.foo" to -3L), "com.foo"))
    }

    @Test
    fun zeroValueStands() {
        assertEquals(0L, memoryBytesFor(mapOf("com.foo" to 0L), "com.foo"))
    }
}
