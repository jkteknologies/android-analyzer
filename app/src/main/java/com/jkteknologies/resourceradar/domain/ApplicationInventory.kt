package com.jkteknologies.resourceradar.domain

import java.text.NumberFormat
import java.util.Locale

/**
 * Installed-application counts (specs/002-home-screen/data-model.md §3).
 *
 * Produced by a full `PackageManager.getInstalledApplications(0)` enumeration
 * (requires QUERY_ALL_PACKAGES, research.md R-01); classification is the pure
 * `isSystemApplication(flags)` bit test in `data/AppClassifier.kt` (FR-005).
 */
data class ApplicationInventory(
    val nonSystemCount: Int,
    val totalCount: Int,
) {
    /** Derived: everything not counted as non-system (V-7). */
    val systemCount: Int get() = totalCount - nonSystemCount

    /**
     * The Applications figure display string, exactly `"N (M)"` with
     * locale-grouped counts (V-9, FR-004, R-12) — e.g. `"36 (121)"`.
     */
    fun displayString(locale: Locale): String {
        val format = NumberFormat.getInstance(locale)
        return "${format.format(nonSystemCount)} (${format.format(totalCount)})"
    }

    companion object {
        /** `nonSystemCount ≥ 0`, `totalCount ≥ 0`, `nonSystemCount ≤ totalCount`, else `null` (V-7). */
        fun create(nonSystemCount: Int, totalCount: Int): ApplicationInventory? =
            if (nonSystemCount >= 0 && totalCount >= 0 && nonSystemCount <= totalCount) {
                ApplicationInventory(nonSystemCount = nonSystemCount, totalCount = totalCount)
            } else {
                null
            }
    }
}
