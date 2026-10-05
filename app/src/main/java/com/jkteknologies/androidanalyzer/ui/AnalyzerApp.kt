package com.jkteknologies.androidanalyzer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jkteknologies.androidanalyzer.R

/**
 * The two destinations of the hand-rolled shell (data-model §5, R-08):
 * no Navigation dependency — a plain `mutableStateOf` drives the switch and
 * the footer indication, so the two can never diverge.
 */
enum class Destination {
    HOME,
    SETTINGS,
}

/**
 * App shell (task T022; ui-contracts U-1..U-6): a persistent footer on every
 * screen with exactly two buttons — "Home screen" left, "Settings" right
 * (FR-007) — the current destination indicated filled vs tonal (FR-008), and
 * the system back gesture on settings returning home (U-5). Entering HOME
 * re-triggers the home read cycle: [com.jkteknologies.androidanalyzer.ui.home.HomeScreen]
 * leaves composition on SETTINGS and its entering-composition trigger fires
 * again on return (FR-011 in-app return).
 */
@Composable
fun AnalyzerApp(
    home: @Composable () -> Unit,
    settings: @Composable () -> Unit,
) {
    var destination by remember { mutableStateOf(Destination.HOME) }

    BackHandler(enabled = destination == Destination.SETTINGS) {
        destination = Destination.HOME
    }

    Scaffold(
        bottomBar = { FooterBar(destination = destination, onNavigate = { destination = it }) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (destination) {
                Destination.HOME -> home()
                Destination.SETTINGS -> settings()
            }
        }
    }
}

/**
 * The persistent two-button footer (FR-007/FR-008, A-1): exactly "Home screen"
 * and "Settings", nothing else. The current destination renders filled and the
 * other tonal; both buttons carry button role and selected-state semantics.
 */
@Composable
private fun FooterBar(
    destination: Destination,
    onNavigate: (Destination) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FooterButton(
            label = stringResource(R.string.footer_home),
            selected = destination == Destination.HOME,
            onClick = { onNavigate(Destination.HOME) },
        )
        FooterButton(
            label = stringResource(R.string.footer_settings),
            selected = destination == Destination.SETTINGS,
            onClick = { onNavigate(Destination.SETTINGS) },
        )
    }
}

/** One footer button: filled when it is the current destination, tonal otherwise (FR-008). */
@Composable
private fun RowScope.FooterButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val modifier = Modifier
        .weight(1f)
        .semantics { this.selected = selected }
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        FilledTonalButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}
