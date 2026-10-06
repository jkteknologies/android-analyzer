package com.jkteknologies.androidanalyzer.ui.home

import com.jkteknologies.androidanalyzer.domain.ApplicationInventory
import com.jkteknologies.androidanalyzer.domain.BatteryReading
import com.jkteknologies.androidanalyzer.domain.CoreCount
import com.jkteknologies.androidanalyzer.domain.MemoryReading
import com.jkteknologies.androidanalyzer.domain.StorageReading
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locale-aware figure value formatting (task T012, research.md R-12): template
 * composition, locale grouping, battery percent. The byte formatter is injected
 * because `android.text.format.Formatter` is an Android class that throws on
 * the JVM (R-14) — a deterministic fake pins the template plumbing.
 */
class FigureFormattingTest {

    private val strings = FigureStrings(
        memoryValue = "Available %1\$s · Allocated %2\$s",
        storageValue = "Free %1\$s · Used %2\$s",
        batteryValue = "%1\$d%% · %2\$s",
        applicationsValue = "%1\$s (%2\$s)",
        charging = "Charging",
        notCharging = "Not charging",
    )

    // Deterministic byte fake: exact template plumbing is visible in assertions.
    private val formatKb = { bytes: Long -> "${bytes / 1024} KB" }

    private fun formatting(locale: Locale = Locale.US) =
        FigureFormatting(locale = locale, formatBytes = formatKb, strings = strings)

    @Test
    fun `memory value composes available and allocated from the T003 template`() {
        val reading = MemoryReading.create(totalBytes = 10_240, availableBytes = 4_096)!!
        assertEquals("Available 4 KB · Allocated 6 KB", formatting().memoryValue(reading))
    }

    @Test
    fun `storage value composes free and used from the T003 template`() {
        val reading = StorageReading.create(totalBytes = 8_192, availableBytes = 2_048)!!
        assertEquals("Free 2 KB · Used 6 KB", formatting().storageValue(reading))
    }

    @Test
    fun `battery value is whole percent dot separator charging state`() {
        val charging = BatteryReading.create(levelPercent = 87, charging = true)!!
        assertEquals("87% · Charging", formatting().batteryValue(charging))
        val discharging = BatteryReading.create(levelPercent = 5, charging = false)!!
        assertEquals("5% · Not charging", formatting().batteryValue(discharging))
    }

    @Test
    fun `processor value is the plain grouped count`() {
        assertEquals("8", formatting().processorValue(CoreCount(8)))
        assertEquals("16", formatting().processorValue(CoreCount(16)))
    }

    @Test
    fun `applications value composes the N (M) template with grouped counts`() {
        val inventory = ApplicationInventory.create(nonSystemCount = 36, totalCount = 121)!!
        assertEquals("36 (121)", formatting().applicationsValue(inventory))
        val large = ApplicationInventory.create(nonSystemCount = 1234, totalCount = 1_234_567)!!
        assertEquals("1,234 (1,234,567)", formatting().applicationsValue(large))
        assertEquals("1.234 (1.234.567)", formatting(Locale.GERMANY).applicationsValue(large))
    }
}
