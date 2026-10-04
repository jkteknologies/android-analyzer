package com.jkteknologies.androidanalyzer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jkteknologies.androidanalyzer.R

/**
 * Stateless, parameter-free placeholder screen (S-3).
 *
 * Renders a Material 3 [Surface] filling the entire screen and centers the app
 * name from the sole string resource (U-1). No state, no side effects: the
 * output is identical on every launch, rotation, and process death (U-6), and
 * nothing here performs network or background work (U-5).
 */
@Composable
fun PlaceholderScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}
