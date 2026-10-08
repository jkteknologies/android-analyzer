package com.jkteknologies.resourceradar.data

import android.content.Context
import com.jkteknologies.resourceradar.domain.RefreshMode
import com.jkteknologies.resourceradar.domain.fromPersisted

/**
 * [RefreshModeStore] over `SharedPreferences` (004 R-09): the existing
 * `android_analyzer` prefs file, one string key `refresh_mode` holding the
 * enum name — the [SharedPreferencesThemeStore] shape mirrored exactly.
 * `load()` maps missing/corrupt values to [RefreshMode.ON_DEMAND] via the
 * tested pure mapping; `save()` writes with `apply()` — async, no
 * main-thread disk I/O on the selection path.
 */
class SharedPreferencesRefreshModeStore(context: Context) : RefreshModeStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): RefreshMode = RefreshMode.fromPersisted(prefs.getString(KEY, null))

    override fun save(mode: RefreshMode) {
        prefs.edit().putString(KEY, mode.name).apply()
    }

    private companion object {
        const val PREFS_NAME = "android_analyzer"
        const val KEY = "refresh_mode"
    }
}
