package com.jkteknologies.androidanalyzer.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Data-model validation rules V-7 and V-9 (specs/002-home-screen/data-model.md §3, §7):
 * count invariants and the exact "N (M)" display form with locale grouping (FR-004).
 */
class ApplicationInventoryTest {

    // ---------------------------------------------------------------- V-7

    @Test
    fun `accepts non-negative counts with non-system at most total`() {
        val inventory = ApplicationInventory.create(nonSystemCount = 36, totalCount = 121)!!
        assertEquals(36, inventory.nonSystemCount)
        assertEquals(121, inventory.totalCount)
    }

    @Test
    fun `rejects negative counts with null`() {
        assertNull(ApplicationInventory.create(nonSystemCount = -1, totalCount = 121))
        assertNull(ApplicationInventory.create(nonSystemCount = 0, totalCount = -1))
    }

    @Test
    fun `rejects non-system count above total with null`() {
        assertNull(ApplicationInventory.create(nonSystemCount = 122, totalCount = 121))
        assertNull(ApplicationInventory.create(nonSystemCount = 1, totalCount = 0))
    }

    @Test
    fun `systemCount derives as total minus non-system`() {
        assertEquals(85, ApplicationInventory.create(nonSystemCount = 36, totalCount = 121)!!.systemCount)
        assertEquals(121, ApplicationInventory.create(nonSystemCount = 0, totalCount = 121)!!.systemCount)
        assertEquals(0, ApplicationInventory.create(nonSystemCount = 5, totalCount = 5)!!.systemCount)
    }

    @Test
    fun `empty inventory is valid`() {
        val inventory = ApplicationInventory.create(nonSystemCount = 0, totalCount = 0)!!
        assertEquals("0 (0)", inventory.displayString(Locale.US))
    }

    // ---------------------------------------------------------------- V-9

    @Test
    fun `display string is exactly N (M) for the spec examples`() {
        assertEquals(
            "36 (121)",
            ApplicationInventory.create(nonSystemCount = 36, totalCount = 121)!!.displayString(Locale.US),
        )
        assertEquals(
            "0 (121)",
            ApplicationInventory.create(nonSystemCount = 0, totalCount = 121)!!.displayString(Locale.US),
        )
    }

    @Test
    fun `display string groups large counts per locale`() {
        val inventory = ApplicationInventory.create(nonSystemCount = 1234, totalCount = 1_234_567)!!
        assertEquals("1,234 (1,234,567)", inventory.displayString(Locale.US))
        assertEquals("1.234 (1.234.567)", inventory.displayString(Locale.GERMANY))
    }
}
