package com.jkteknologies.androidanalyzer.domain

/**
 * One processor core tier: the logical cores sharing a distinct maximum
 * frequency (004 FR-013, data-model §4).
 */
data class CoreTier(
    val count: Int,
    val maxFrequencyHz: Long,
)

/**
 * The processor figure's value (004 FR-013, SC-008): per-type core counts
 * where the device distinguishes them, one tier where it does not — the
 * single-tier shape is FR-013's fallback and renders exactly like 002's
 * plain core count (V-A4b).
 */
data class CoreTiers(
    val totalCount: Int,
    val tiers: List<CoreTier>,
) {
    companion object {
        /**
         * V-A4: `totalCount ≥ 1`; tiers non-empty; each `count ≥ 1`; tier
         * counts sum to `totalCount`; frequencies distinct and `> 0`. The
         * result is ordered ascending by frequency; a violation maps to
         * `null` → [FigureUiState.Unavailable], never a clamped shape.
         */
        fun create(totalCount: Int, tiers: List<CoreTier>): CoreTiers? {
            val valid = totalCount >= 1 &&
                tiers.isNotEmpty() &&
                tiers.all { it.count >= 1 && it.maxFrequencyHz > 0 } &&
                tiers.sumOf { it.count } == totalCount &&
                tiers.map { it.maxFrequencyHz }.toSet().size == tiers.size
            return if (valid) {
                CoreTiers(totalCount, tiers.sortedBy { it.maxFrequencyHz })
            } else {
                null
            }
        }
    }
}
