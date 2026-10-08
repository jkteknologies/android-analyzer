package com.jkteknologies.resourceradar.domain

import java.text.Collator

/**
 * The system/user mark of one installed application (004 FR-004, data-model
 * §1). Produced only by the shared `isSystemApplication` bit test — never a
 * second rule (research.md R-01, contract clause 2).
 */
enum class AppClassification {
    SYSTEM,
    USER,
}

/**
 * One application in the Details inventory (004 FR-004/FR-006/FR-007,
 * data-model §1). `packageName` is the identity; duplicate display names are
 * allowed. Byte figures are `null` = not available — never a substitute
 * value.
 */
data class InstalledApp(
    val packageName: String,
    val displayName: String,
    val classification: AppClassification,
    val storageBytes: Long?,
    val memoryBytes: Long?,
) {
    companion object {
        /**
         * V-A1: both names non-blank; any non-null byte value `≥ 0`. A
         * violation maps to `null` — one invalid app fails the whole
         * inventory read (never-clamp policy, 004 FR-010).
         */
        fun create(
            packageName: String,
            displayName: String,
            classification: AppClassification,
            storageBytes: Long? = null,
            memoryBytes: Long? = null,
        ): InstalledApp? =
            if (packageName.isBlank() || displayName.isBlank() ||
                (storageBytes != null && storageBytes < 0) ||
                (memoryBytes != null && memoryBytes < 0)
            ) {
                null
            } else {
                InstalledApp(packageName, displayName, classification, storageBytes, memoryBytes)
            }
    }
}

/**
 * The sorted full inventory from one `InstalledAppReader` pass (004
 * data-model §2). Ordering by display name (locale `Collator`, primary
 * strength) is owned by this factory — readers hand over unsorted entries
 * (contract clause 4).
 */
data class AppInventory(val apps: List<InstalledApp>) {

    /** Derived: every installed application. */
    val totalCount: Int get() = apps.size

    /** Derived: user-installed applications. */
    val userCount: Int get() = apps.count { it.classification == AppClassification.USER }

    /** Derived: system applications. */
    val systemCount: Int get() = apps.count { it.classification == AppClassification.SYSTEM }

    companion object {
        /**
         * V-A2: package names unique; the empty list is allowed (it renders
         * the filter's explanatory empty state). The result is sorted by
         * display name with a primary-strength Collator (case- and
         * accent-insensitive).
         */
        fun create(apps: List<InstalledApp>): AppInventory? {
            val packageNames = HashSet<String>(apps.size)
            if (!apps.all { packageNames.add(it.packageName) }) return null
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            return AppInventory(apps.sortedWith(compareBy(collator) { it.displayName }))
        }
    }
}

/**
 * The Details list restriction (004 FR-005, data-model §3). A pure selection
 * over an already-read inventory — applying a filter never triggers a read
 * (SC-007).
 */
enum class AppCategoryFilter {
    ALL,
    USER,
    SYSTEM,
    ;

    /**
     * V-A3: ALL → the full sorted list; USER/SYSTEM → the matching
     * classification subset with the ordering preserved.
     */
    fun apply(inventory: AppInventory): List<InstalledApp> = when (this) {
        ALL -> inventory.apps
        USER -> inventory.apps.filter { it.classification == AppClassification.USER }
        SYSTEM -> inventory.apps.filter { it.classification == AppClassification.SYSTEM }
    }
}
