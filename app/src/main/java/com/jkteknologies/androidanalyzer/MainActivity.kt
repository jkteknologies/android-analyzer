package com.jkteknologies.androidanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jkteknologies.androidanalyzer.data.AndroidDeviceReaders
import com.jkteknologies.androidanalyzer.data.SharedPreferencesThemeStore
import com.jkteknologies.androidanalyzer.ui.AnalyzerApp
import com.jkteknologies.androidanalyzer.ui.home.HomeScreen
import com.jkteknologies.androidanalyzer.ui.home.HomeStateHolder
import com.jkteknologies.androidanalyzer.ui.settings.SettingsScreen
import com.jkteknologies.androidanalyzer.ui.theme.AppTheme

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
        setContent {
            var themePreference by remember { mutableStateOf(themeStore.load()) }
            AppTheme(preference = themePreference) {
                val holder = remember {
                    val readers = AndroidDeviceReaders(this@MainActivity)
                    HomeStateHolder(
                        memoryReader = readers.memoryReader,
                        storageReader = readers.storageReader,
                        batteryReader = readers.batteryReader,
                        coreCountReader = readers.coreCountReader,
                        applicationCounter = readers.applicationCounter,
                        poster = HomeStateHolder.mainThreadPoster(),
                    )
                }
                AnalyzerApp(
                    home = { HomeScreen(holder) },
                    settings = {
                        SettingsScreen(
                            selectedPreference = themePreference,
                            onPreferenceSelected = { selection ->
                                themeStore.save(selection)
                                themePreference = selection
                            },
                        )
                    },
                )
            }
        }
    }
}
