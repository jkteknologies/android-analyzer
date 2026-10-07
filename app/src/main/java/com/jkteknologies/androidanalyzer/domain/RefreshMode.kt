package com.jkteknologies.androidanalyzer.domain

/**
 * The persisted automatic-refresh selection (004 FR-014/FR-015, data-model
 * §6): the user-confirmed option set with `ON_DEMAND` as the default.
 */
enum class RefreshMode {
    ON_DEMAND,
    THIRTY_SECONDS,
    ONE_MINUTE,
    FIVE_MINUTES,
    ;

    /** V-R1: the ticker's sleep — `null` iff [ON_DEMAND]. */
    val intervalMillis: Long?
        get() = when (this) {
            ON_DEMAND -> null
            THIRTY_SECONDS -> 30_000L
            ONE_MINUTE -> 60_000L
            FIVE_MINUTES -> 300_000L
        }

    companion object
}

/**
 * Maps a persisted raw value back to a mode: a missing, unknown, or corrupt
 * value (anything that is not a valid enum name) falls back to
 * [RefreshMode.ON_DEMAND] (V-R2, FR-014) — mirroring
 * `ThemePreference.fromPersisted`; the SharedPreferences store delegates to
 * this (R-09).
 */
fun RefreshMode.Companion.fromPersisted(raw: String?): RefreshMode =
    RefreshMode.entries.firstOrNull { it.name == raw } ?: RefreshMode.ON_DEMAND
