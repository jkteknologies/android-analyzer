package com.jkteknologies.resourceradar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The 004 automatic-refresh domain rules (data-model §9): the interval
 * mapping (V-R1) and the persisted-value mapping (V-R2). Pure Kotlin — no
 * Android imports.
 */
class RefreshModeTest {

    @Test
    fun `intervalMillis is null iff ON_DEMAND`() { // V-R1
        assertNull(RefreshMode.ON_DEMAND.intervalMillis)
        RefreshMode.entries
            .filter { it != RefreshMode.ON_DEMAND }
            .forEach { assertNotNull("expected an interval for $it", it.intervalMillis) }
    }

    @Test
    fun `intervalMillis carries the exact user-confirmed intervals`() { // V-R1
        assertEquals(30_000L, RefreshMode.THIRTY_SECONDS.intervalMillis)
        assertEquals(60_000L, RefreshMode.ONE_MINUTE.intervalMillis)
        assertEquals(300_000L, RefreshMode.FIVE_MINUTES.intervalMillis)
    }

    @Test
    fun `fromPersisted round-trips every enum name`() { // V-R2
        RefreshMode.entries.forEach { mode ->
            assertEquals(mode, RefreshMode.fromPersisted(mode.name))
        }
    }

    @Test
    fun `fromPersisted maps missing unknown or corrupt values to ON_DEMAND`() { // V-R2
        assertEquals(RefreshMode.ON_DEMAND, RefreshMode.fromPersisted(null))
        assertEquals(RefreshMode.ON_DEMAND, RefreshMode.fromPersisted(""))
        assertEquals(RefreshMode.ON_DEMAND, RefreshMode.fromPersisted("garbage"))
        assertEquals(RefreshMode.ON_DEMAND, RefreshMode.fromPersisted("thirty seconds"))
    }
}
