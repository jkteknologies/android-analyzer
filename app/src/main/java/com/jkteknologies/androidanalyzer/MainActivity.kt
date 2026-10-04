package com.jkteknologies.androidanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.jkteknologies.androidanalyzer.ui.PlaceholderScreen

/**
 * Sole Activity (matches the single manifest component, T007): exported with the
 * launcher intent filter. Does nothing but host the placeholder screen — no
 * ViewModel, no saved-instance state, no side effects (U-2, U-3).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PlaceholderScreen()
            }
        }
    }
}
