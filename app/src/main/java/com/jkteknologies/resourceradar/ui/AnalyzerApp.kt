package com.jkteknologies.resourceradar.ui

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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.jkteknologies.resourceradar.domain.AppCategoryFilter
import com.jkteknologies.resourceradar.domain.RefreshMode
import com.jkteknologies.resourceradar.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    refreshMode: RefreshMode,
    refreshHome: () -> Unit,
    refreshDetails: () -> Unit,
    home: @Composable (onOpenApplications: (AppCategoryFilter) -> Unit) -> Unit,
    details: @Composable (pendingFilter: AppCategoryFilter?, onPendingFilterConsumed: () -> Unit) -> Unit,
    settings: @Composable () -> Unit,
) {
    var destination by remember { mutableStateOf(Destination.HOME) }

    /**
     * The Home → Details arrival directive (H-3, FR-012): set together with
     * `destination = DETAILS` by a Home entry tap, applied once by the
     * details slot via `applyPendingFilter` — overriding any previously
     * chosen filter — then cleared. `null` means "no directive": plain tab
     * switches never touch the filter.
     */
    var pendingDetailsFilter by remember { mutableStateOf<AppCategoryFilter?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current

    /**
     * The shared link-failure surface (006 FR-016, contracts L-3): one
     * Scaffold-owned snackbar; a failed link open shows a single transient,
     * auto-dismissing message — no dialog, no crash. Screens receive
     * [onLinkUnavailable] instead of a host so the message stays in one place.
     */
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val linkUnavailableMessage = stringResource(R.string.link_unavailable)
    val onLinkUnavailable = {
        scope.launch { snackbarHostState.showSnackbar(linkUnavailableMessage) }
    }

    /**
     * The auto-refresh ticker (T-1..T-5, FR-014..FR-017): one shell-owned
     * loop that sleeps the interval and refreshes the **visible** screen only
     * (SETTINGS → nothing). `repeatOnLifecycle(RESUMED)` gates it on
     * visibility — no tick below RESUMED, zero background work (Constitution
     * IX); ON_DEMAND (`intervalMillis == null`) leaves it inert while manual
     * pull-to-refresh stays available in every mode; keying on mode and
     * destination applies changes at the next tick boundary — no catch-up
     * burst.
     */
    LaunchedEffect(refreshMode, destination) {
        val interval = refreshMode.intervalMillis ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(interval)
                when (destination) {
                    Destination.HOME -> refreshHome()
                    Destination.DETAILS -> refreshDetails()
                    Destination.SETTINGS -> Unit
                }
            }
        }
    }

    BackHandler(enabled = destination != Destination.HOME) {
        destination = Destination.HOME
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { FooterBar(destination = destination, onNavigate = { destination = it }) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (destination) {
                Destination.HOME -> home { filter ->
                    pendingDetailsFilter = filter
                    destination = Destination.DETAILS
                }
                Destination.DETAILS -> details(pendingDetailsFilter) { pendingDetailsFilter = null }
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
