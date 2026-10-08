package com.jkteknologies.androidanalyzer.domain

/**
 * The single persisted user preference: theme selection (data-model §4).
 * `SYSTEM` is the default (FR-009).
 */
enum class ThemePreference {
    LIGHT,
    DARK,
    SYSTEM;

    companion object
}

/** The scheme the app actually renders after resolving the preference (data-model §4). */
enum class EffectiveTheme {
    LIGHT,
    DARK,
}

/**
 * Effective theme resolution (T-3): `LIGHT → Light`, `DARK → Dark`,
 * `SYSTEM → systemInDarkMode ? Dark : Light` — re-evaluated on every
 * recomposition, so live system switches are followed without restart (FR-006).
 * Pure Kotlin — no Android imports (plan.md Testing).
 */
fun effectiveTheme(preference: ThemePreference, systemInDarkMode: Boolean): EffectiveTheme =
    when (preference) {
        ThemePreference.LIGHT -> EffectiveTheme.LIGHT
        ThemePreference.DARK -> EffectiveTheme.DARK
        ThemePreference.SYSTEM -> if (systemInDarkMode) EffectiveTheme.DARK else EffectiveTheme.LIGHT
    }

/**
 * Maps a persisted raw value back to a preference: a missing or corrupt value
 * (anything that is not a valid enum name) falls back to [ThemePreference.SYSTEM]
 * (V-10, FR-009). The SharedPreferences adapter delegates to this (R-07).
 */
fun ThemePreference.Companion.fromPersisted(raw: String?): ThemePreference =
    ThemePreference.entries.firstOrNull { it.name == raw } ?: ThemePreference.SYSTEM
