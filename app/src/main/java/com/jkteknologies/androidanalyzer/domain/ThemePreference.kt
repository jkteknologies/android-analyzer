package com.jkteknologies.androidanalyzer.domain

/**
 * The single persisted user preference: theme selection (data-model §4).
 *
 * `SYSTEM` is the default (FR-009). Required early because the reader seam
 * (data/DeviceReaders.kt, contracts/device-readers.md) types its
 * `ThemePreferenceStore` against it; effective-theme resolution and the
 * persisted-value fallback land with US2 (T018).
 */
enum class ThemePreference {
    LIGHT,
    DARK,
    SYSTEM,
}
