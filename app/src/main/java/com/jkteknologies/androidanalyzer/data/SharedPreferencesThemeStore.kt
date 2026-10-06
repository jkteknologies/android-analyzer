package com.jkteknologies.androidanalyzer.data

import android.content.Context
import com.jkteknologies.androidanalyzer.domain.ThemePreference
import com.jkteknologies.androidanalyzer.domain.fromPersisted

/**
 * [ThemePreferenceStore] over `SharedPreferences` (task T019; research.md
 * R-07): a single string key `theme_preference` holding the enum name.
 * `load()` maps missing/corrupt values to [ThemePreference.SYSTEM] via the
 * tested pure mapping; `save()` writes with `apply()` — async, no main-thread
 * disk I/O on the selection path.
 */
class SharedPreferencesThemeStore(context: Context) : ThemePreferenceStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): ThemePreference = ThemePreference.fromPersisted(prefs.getString(KEY, null))

    override fun save(preference: ThemePreference) {
        prefs.edit().putString(KEY, preference.name).apply()
    }

    private companion object {
        const val PREFS_NAME = "android_analyzer"
        const val KEY = "theme_preference"
    }
}
