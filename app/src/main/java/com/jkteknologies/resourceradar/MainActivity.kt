package com.jkteknologies.resourceradar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jkteknologies.resourceradar.data.AndroidDeviceReaders
import com.jkteknologies.resourceradar.data.SharedPreferencesThemeStore
import com.jkteknologies.resourceradar.data.SharedPreferencesRefreshModeStore
import com.jkteknologies.resourceradar.ui.AnalyzerApp
import com.jkteknologies.resourceradar.ui.details.DetailsScreen
import com.jkteknologies.resourceradar.ui.details.DetailsStateHolder
import com.jkteknologies.resourceradar.ui.help.HelpScreen
import com.jkteknologies.resourceradar.ui.home.HomeScreen
import com.jkteknologies.resourceradar.ui.home.HomeStateHolder
import com.jkteknologies.resourceradar.ui.settings.SettingsScreen
import com.jkteknologies.resourceradar.ui.theme.AppTheme

/**
 * Sole Activity (single manifest component): hosts the app shell under
 * [AppTheme]. The theme preference is the app-state single source of truth
 * (data-model §6): loaded once at creation via the store (R-07), lifted into
 * Compose state, and on selection (task T027) persisted FIRST with `save()`
 * then applied by updating the state — [AppTheme] recomposes immediately, no
 * restart (FR-010). A corrupt persisted value already resolves to SYSTEM
 * (V-10). Home read cycles are triggered inside [HomeScreen] per FR-011.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeStore = SharedPreferencesThemeStore(this)
        val refreshModeStore = SharedPreferencesRefreshModeStore(this)
        setContent {
            var themePreference by remember { mutableStateOf(themeStore.load()) }
            var refreshMode by remember { mutableStateOf(refreshModeStore.load()) }
            AppTheme(preference = themePreference) {
                val readers = remember { AndroidDeviceReaders(this@MainActivity) }
                val holder = remember {
                    HomeStateHolder(
                        memoryReader = readers.memoryReader,
                        storageReader = readers.storageReader,
                        batteryReader = readers.batteryReader,
                        coreTierReader = readers.coreTierReader,
                        applicationCounter = readers.applicationCounter,
                        poster = HomeStateHolder.mainThreadPoster(),
                    )
                }
                val detailsHolder = remember {
                    DetailsStateHolder(
                        installedAppReader = readers.installedAppReader,
                        usageAccessStatus = readers.usageAccessStatus,
                        shizukuAccessStatus = readers.shizukuAccessStatus,
                        shizukuAuthorizer = readers.shizukuAuthorizer,
                        shizukuChangeSource = readers.shizukuChangeSource,
                        poster = HomeStateHolder.mainThreadPoster(),
                    )
                }
                AnalyzerApp(
                    refreshMode = refreshMode,
                    refreshHome = holder::refresh,
                    refreshDetails = detailsHolder::refresh,
                    home = { onOpenApplications ->
                        HomeScreen(holder, onOpenApplications)
                    },
                    details = { pendingFilter, onPendingFilterConsumed ->
                        // H-3 consumption: apply the arrival directive synchronously
                        // before the screen composes — no full-list flash on arrival —
                        // then clear the shell slot (once). Plain recomposition with a
                        // null directive never touches the filter.
                        pendingFilter?.let {
                            detailsHolder.applyPendingFilter(it)
                            onPendingFilterConsumed()
                        }
                        DetailsScreen(detailsHolder)
                    },
                    settings = {
                        SettingsScreen(
                            selectedPreference = themePreference,
                            onPreferenceSelected = { selection ->
                                themeStore.save(selection)
                                themePreference = selection
                            },
                            selectedRefreshMode = refreshMode,
                            onRefreshModeSelected = { selection ->
                                // S-2/W-1: persist first, then update the lifted state —
                                // the same save-then-apply order as the theme (002 T-027).
                                refreshModeStore.save(selection)
                                refreshMode = selection
                            },
                        )
                    },
                    help = { onLinkUnavailable ->
                        HelpScreen(onLinkUnavailable)
                    },
                )
            }
        }
    }
}
