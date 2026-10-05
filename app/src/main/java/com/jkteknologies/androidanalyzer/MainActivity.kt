package com.jkteknologies.androidanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import com.jkteknologies.androidanalyzer.data.AndroidDeviceReaders
import com.jkteknologies.androidanalyzer.ui.home.HomeScreen
import com.jkteknologies.androidanalyzer.ui.home.HomeStateHolder

/**
 * Sole Activity (single manifest component): hosts the home screen and owns
 * its state holder + platform readers (task T015). A bare [MaterialTheme]
 * stands in until US2's `AppTheme` lands (T020). Read cycles are triggered
 * inside [HomeScreen] per FR-011 (launch, in-app return, background resume).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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
                HomeScreen(holder)
            }
        }
    }
}
