package com.jkteknologies.androidanalyzer.ui.home

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jkteknologies.androidanalyzer.R
import com.jkteknologies.androidanalyzer.domain.ApplicationInventory
import com.jkteknologies.androidanalyzer.domain.BatteryReading
import com.jkteknologies.androidanalyzer.domain.CoreCount
import com.jkteknologies.androidanalyzer.domain.MemoryReading
import com.jkteknologies.androidanalyzer.domain.StorageReading
import java.text.NumberFormat
import java.util.Locale

/**
 * The T003 value templates and charging labels, resolved from resources at the
 * UI boundary so [FigureFormatting] itself stays JVM-testable with literal
 * strings (research.md R-12, R-14).
 */
data class FigureStrings(
    val memoryValue: String,       // "Available %1$s · Allocated %2$s"
    val storageValue: String,      // "Free %1$s · Used %2$s"
    val batteryValue: String,      // "%1$d%% · %2$s"
    val charging: String,          // "Charging"
    val notCharging: String,       // "Not charging"
)

/**
 * Locale-aware value formatting for the five figures (R-12): byte amounts via
 * the injected [formatBytes] (production uses `Formatter.formatShortFileSize`,
 * an Android class that throws on the JVM), counts via
 * [NumberFormat.getInstance][NumberFormat.getInstance] with locale grouping,
 * battery as a whole percent, charging state and templates from [FigureStrings].
 */
class FigureFormatting(
    private val locale: Locale,
    private val formatBytes: (Long) -> String,
    private val strings: FigureStrings,
) {
    fun memoryValue(reading: MemoryReading): String = String.format(
        locale,
        strings.memoryValue,
        formatBytes(reading.availableBytes),
        formatBytes(reading.allocatedBytes),
    )

    fun storageValue(reading: StorageReading): String = String.format(
        locale,
        strings.storageValue,
        formatBytes(reading.availableBytes),
        formatBytes(reading.usedBytes),
    )

    fun batteryValue(reading: BatteryReading): String = String.format(
        locale,
        strings.batteryValue,
        reading.levelPercent,
        if (reading.charging) strings.charging else strings.notCharging,
    )

    fun processorValue(cores: CoreCount): String = count(cores.count)

    /** 004 H-1: the user-installed count as a plain locale-grouped number. */
    fun userApplicationsValue(inventory: ApplicationInventory): String = count(inventory.nonSystemCount)

    /** 004 H-1: the derived system count as a plain locale-grouped number. */
    fun systemApplicationsValue(inventory: ApplicationInventory): String = count(inventory.systemCount)

    private fun count(value: Int): String = NumberFormat.getInstance(locale).format(value)
}

/** Production wiring: byte formatting via the platform, strings via resources, locale via configuration. */
@Composable
fun rememberFigureFormatting(): FigureFormatting {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val strings = FigureStrings(
        memoryValue = stringResource(R.string.memory_value),
        storageValue = stringResource(R.string.storage_value),
        batteryValue = stringResource(R.string.battery_value),
        charging = stringResource(R.string.charging_charging),
        notCharging = stringResource(R.string.charging_not_charging),
    )
    return remember(context, locale, strings) {
        FigureFormatting(
            locale = locale,
            formatBytes = { bytes -> Formatter.formatShortFileSize(context, bytes) },
            strings = strings,
        )
    }
}
