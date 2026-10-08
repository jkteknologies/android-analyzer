package com.jkteknologies.resourceradar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The 004 processor-tier domain rules (data-model §9, V-A4): the factory's
 * sum/positivity/distinct-frequency validation, ascending ordering, and the
 * single-tier fallback shape. Pure Kotlin — no Android imports.
 */
class CoreTiersTest {

    @Test
    fun `create rejects a non-positive total count`() { // V-A4
        assertNull(CoreTiers.create(0, listOf(CoreTier(1, 1_800_000_000))))
    }

    @Test
    fun `create rejects an empty tier list`() { // V-A4
        assertNull(CoreTiers.create(8, emptyList()))
    }

    @Test
    fun `create rejects a tier count below one`() { // V-A4
        assertNull(CoreTiers.create(8, listOf(CoreTier(0, 1_800_000_000), CoreTier(8, 2_400_000_000))))
    }

    @Test
    fun `create rejects a non-positive frequency`() { // V-A4
        assertNull(CoreTiers.create(8, listOf(CoreTier(4, 0), CoreTier(4, 2_400_000_000))))
        assertNull(CoreTiers.create(8, listOf(CoreTier(4, -1), CoreTier(4, 2_400_000_000))))
    }

    @Test
    fun `create rejects duplicated frequencies`() { // V-A4
        assertNull(CoreTiers.create(8, listOf(CoreTier(4, 1_800_000_000), CoreTier(4, 1_800_000_000))))
    }

    @Test
    fun `create rejects tier counts not summing to the total`() { // V-A4
        assertNull(CoreTiers.create(8, listOf(CoreTier(4, 1_800_000_000), CoreTier(3, 2_400_000_000))))
    }

    @Test
    fun `create orders valid tiers ascending by frequency`() { // V-A4
        val tiers = CoreTiers.create(
            8,
            listOf(CoreTier(4, 2_400_000_000), CoreTier(4, 1_800_000_000)),
        )!!

        assertEquals(listOf(1_800_000_000L, 2_400_000_000L), tiers.tiers.map { it.maxFrequencyHz })
    }

    @Test
    fun `the single-tier shape is valid — FR-013's fallback`() { // V-A4
        val single = CoreTiers.create(8, listOf(CoreTier(8, 1_800_000_000)))!!

        assertEquals(8, single.totalCount)
        assertEquals(1, single.tiers.size)
    }
}
