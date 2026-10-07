package com.jkteknologies.androidanalyzer.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.jkteknologies.androidanalyzer.R

/**
 * The three destinations of the hand-rolled shell (004 data-model §8, R-05):
 * no Navigation dependency — a plain `mutableStateOf` drives the switch and
 * the footer indication, so the two can never diverge.
 */
enum class Destination {
    HOME,
    DETAILS,
    SETTINGS,
}

/**
 * App shell (002 U-1..U-6 plus 004 contracts/navigation-and-footer.md
 * N-1..N-4): a persistent footer on every screen with three equal-width
 * square-cornered gapless buttons — Home, Details, Settings (FR-002) — the
 * current destination indicated filled vs tonal (FR-003), and the system back
 * gesture on DETAILS and SETTINGS returning HOME (N-4); back from HOME exits
 * as today. Entering HOME re-triggers the home read cycle: the home screen
 * leaves composition on other tabs and its entering-composition trigger
 * fires again on return (002 in-app return).
 */
@Composable
fun AnalyzerApp(
    home: @Composable () -> Unit,
    details: @Composable () -> Unit,
    settings: @Composable () -> Unit,
) {
    var destination by remember { mutableStateOf(Destination.HOME) }

    BackHandler(enabled = destination != Destination.HOME) {
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
                Destination.DETAILS -> details()
                Destination.SETTINGS -> settings()
            }
        }
    }
}

/**
 * The persistent three-button footer (N-2, FR-002): one continuous full-width
 * bar — no outer padding, no spacing between buttons, three equal-width
 * (`weight(1f)`) square-cornered buttons. Default Material3 button heights;
 * no fixed heights (002 A-2 rule preserved).
 */
@Composable
private fun FooterBar(
    destination: Destination,
    onNavigate: (Destination) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        FooterButton(
            label = stringResource(R.string.footer_home),
            selected = destination == Destination.HOME,
            onClick = { onNavigate(Destination.HOME) },
        )
        FooterButton(
            label = stringResource(R.string.footer_details),
            selected = destination == Destination.DETAILS,
            onClick = { onNavigate(Destination.DETAILS) },
        )
        FooterButton(
            label = stringResource(R.string.footer_settings),
            selected = destination == Destination.SETTINGS,
            onClick = { onNavigate(Destination.SETTINGS) },
        )
    }
}

/**
 * One footer button (FR-003): filled when it is the current destination,
 * tonal otherwise — both square-cornered, carrying button role and
 * selected-state semantics.
 */
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
        Button(onClick = onClick, modifier = modifier, shape = RectangleShape) { Text(label) }
    } else {
        FilledTonalButton(onClick = onClick, modifier = modifier, shape = RectangleShape) { Text(label) }
    }
}
