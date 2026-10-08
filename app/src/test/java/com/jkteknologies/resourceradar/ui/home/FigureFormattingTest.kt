package com.jkteknologies.resourceradar.ui.home

import com.jkteknologies.resourceradar.domain.ApplicationInventory
import com.jkteknologies.resourceradar.domain.BatteryReading
import com.jkteknologies.resourceradar.domain.CoreTier
import com.jkteknologies.resourceradar.domain.CoreTiers
import com.jkteknologies.resourceradar.domain.MemoryReading
import com.jkteknologies.resourceradar.domain.StorageReading
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
        processorTiersValue = "%1\$d cores: %2\$s",
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
    fun `processor single tier renders the plain grouped count — byte-identical to 002`() { // V-A4b
        val single = CoreTiers.create(8, listOf(CoreTier(8, 1_800_000_000)))!!
        assertEquals("8", formatting().processorValue(single))
        val sixteen = CoreTiers.create(16, listOf(CoreTier(16, 999_999_999)))!!
        assertEquals("16", formatting().processorValue(sixteen))
    }

    @Test
    fun `processor multi tier renders N cores colon tiers with one-decimal GHz`() { // V-A4b
        val bigLittle = CoreTiers.create(
            8,
            listOf(CoreTier(4, 2_400_000_000), CoreTier(4, 1_800_000_000)),
        )!!
        assertEquals("8 cores: 4 × 1.8 GHz + 4 × 2.4 GHz", formatting().processorValue(bigLittle))
    }

    @Test
    fun `user applications value is the grouped non-system count`() { // 004 T015
        val inventory = ApplicationInventory.create(nonSystemCount = 36, totalCount = 121)!!
        assertEquals("36", formatting().userApplicationsValue(inventory))
        val large = ApplicationInventory.create(nonSystemCount = 1234, totalCount = 1_234_567)!!
        assertEquals("1,234", formatting().userApplicationsValue(large))
        assertEquals("1.234", formatting(Locale.GERMANY).userApplicationsValue(large))
    }

    @Test
    fun `system applications value is the grouped derived system count`() { // 004 T015
        val inventory = ApplicationInventory.create(nonSystemCount = 36, totalCount = 121)!!
        assertEquals("85", formatting().systemApplicationsValue(inventory))
        val none = ApplicationInventory.create(nonSystemCount = 5, totalCount = 5)!!
        assertEquals("0", formatting().systemApplicationsValue(none))
    }
}
