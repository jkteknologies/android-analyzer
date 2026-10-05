package com.jkteknologies.androidanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.jkteknologies.androidanalyzer.data.AndroidDeviceReaders
import com.jkteknologies.androidanalyzer.data.SharedPreferencesThemeStore
import com.jkteknologies.androidanalyzer.ui.AnalyzerApp
import com.jkteknologies.androidanalyzer.ui.home.HomeScreen
import com.jkteknologies.androidanalyzer.ui.home.HomeStateHolder
import com.jkteknologies.androidanalyzer.ui.settings.SettingsScreen
import com.jkteknologies.androidanalyzer.ui.theme.AppTheme

/**
 * Sole Activity (single manifest component): hosts the app shell under
 * [AppTheme] (task T024). The theme preference is loaded once here at creation
 * via the store (R-07); `SYSTEM` default means the app follows the device theme
 * live (FR-006). Manual selection state lifts into this composition with US4
 * (T027). Home read cycles are triggered inside [HomeScreen] per FR-011.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeStore = SharedPreferencesThemeStore(this)
        setContent {
            val themePreference = remember { themeStore.load() }
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
                    settings = { SettingsScreen() },
                )
            }
        }
    }
}
