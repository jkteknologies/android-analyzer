package com.jkteknologies.resourceradar.domain

/**
 * Pure domain models for the home-screen figures (specs/002-home-screen/
 * data-model.md §1–2). No Android imports — this is what makes the validation
 * rules V-1..V-6 unit-testable on the JVM (plan.md Testing).
 */

/**
 * The universal three-state lifecycle of every displayed figure (FR-012, FR-014).
 *
 * Transitions are strict (V-1): a read cycle starts by resetting every figure to
 * [Loading]; a read then lands in [Available] (value valid) or [Unavailable]
 * (reader threw / returned null / validation failed). [Available] and
 * [Unavailable] are terminal until the next cycle resets to [Loading]. Figures
 * are independent — no figure waits for another (FR-014).
 */
sealed interface FigureUiState<out T> {

    /** Read in flight; no value yet. Renders the neutral placeholder (H-4). */
    data object Loading : FigureUiState<Nothing>

    /** Read completed successfully. */
    data class Available<T>(val value: T) : FigureUiState<T>

    /** Read failed or returned a nonsensical value (FR-012). */
    data object Unavailable : FigureUiState<Nothing>
}

/**
 * Device memory from one `ActivityManager.MemoryInfo` read (R-02).
 * Validation failures map to `null` → figure [FigureUiState.Unavailable],
 * never a clamped value (V-4, FR-012).
 */
data class MemoryReading(
    val totalBytes: Long,
    val availableBytes: Long,
) {
    /** Derived from the same single reading: total − available (V-2, FR-002). */
    val allocatedBytes: Long get() = totalBytes - availableBytes

    companion object {
        /** `totalBytes > 0` and `0 ≤ availableBytes ≤ totalBytes`, else `null` (V-4). */
        fun create(totalBytes: Long, availableBytes: Long): MemoryReading? =
            if (totalBytes > 0 && availableBytes in 0..totalBytes) {
                MemoryReading(totalBytes = totalBytes, availableBytes = availableBytes)
            } else {
                null
            }
    }
}

/**
 * Internal storage (the data partition) from one `StatFs` read (R-03).
 * Same validation policy as [MemoryReading] (V-4).
 */
data class StorageReading(
    val totalBytes: Long,
    val availableBytes: Long,
) {
    /** Derived from the same single reading: total − available (V-2, FR-002). */
    val usedBytes: Long get() = totalBytes - availableBytes

    companion object {
        /** `totalBytes > 0` and `0 ≤ availableBytes ≤ totalBytes`, else `null` (V-4). */
        fun create(totalBytes: Long, availableBytes: Long): StorageReading? =
            if (totalBytes > 0 && availableBytes in 0..totalBytes) {
                StorageReading(totalBytes = totalBytes, availableBytes = availableBytes)
            } else {
                null
            }
    }
}

/**
 * Battery level and charging state from one sticky
 * `ACTION_BATTERY_CHANGED` read (R-04). `0 ≤ levelPercent ≤ 100`, else `null` (V-5).
 */
data class BatteryReading(
    val levelPercent: Int,
    val charging: Boolean,
) {
    companion object {
        fun create(levelPercent: Int, charging: Boolean): BatteryReading? =
            if (levelPercent in 0..100) {
                BatteryReading(levelPercent = levelPercent, charging = charging)
            } else {
                null
            }
    }
}

/** Processor core count from `Runtime.availableProcessors()` (R-05). `count ≥ 1` (V-6). */
data class CoreCount(val count: Int) {
    companion object {
        fun create(count: Int): CoreCount? = if (count >= 1) CoreCount(count) else null
    }
}
