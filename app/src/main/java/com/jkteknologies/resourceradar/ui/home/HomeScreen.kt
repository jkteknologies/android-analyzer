package com.jkteknologies.resourceradar.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.jkteknologies.resourceradar.R
import com.jkteknologies.resourceradar.domain.AppCategoryFilter
import com.jkteknologies.resourceradar.domain.FigureUiState

/**
 * The device-overview home screen (002 ui-contracts H-1..H-9; 004 H-1/H-2).
 *
 * Five figure groups — Memory, Internal storage, Battery, Processor, and
 * since 004 the applications pair: **User applications** and **System
 * applications** as two separate tappable entries (FR-011) sharing the one
 * applications figure state. The layout renders immediately; each figure
 * independently shows the neutral placeholder, its formatted value, or the
 * distinct "Not available" indication.
 *
 * Read-cycle triggers (FR-011, R-10): `ON_RESUME` (launch and background
 * resume) via a [DefaultLifecycleObserver], plus entering composition while
 * already resumed (in-app return). Nothing runs while home is not visible: the
 * observer detaches and the executor shuts down on dispose — returning to
 * home starts the next cycle on a fresh executor.
 *
 * Pull-to-refresh (003 refresh-interaction W-1..W-4): the scrollable
 * figure column is wrapped in a [PullToRefreshBox] from *outside*, so the
 * gesture arms only while the content is scrolled to its top (FR-009) and
 * leaves the system's top-edge gestures untouched (FR-008). The default
 * Material3 indicator follows [HomeStateHolder.isRefreshing]; releasing past
 * the trigger distance calls [HomeStateHolder.refresh] (FR-001/FR-002).
 *
 * Accessibility (003 W-5): the figure column exposes a "Refresh
 * figures" custom accessibility action — a screen-reader trigger path that
 * does not depend on the touch gesture — and a polite live-region status node
 * announces "Refreshing figures" / "Figures refreshed" as the cycle runs and
 * completes.
 */
@Composable
fun HomeScreen(
    holder: HomeStateHolder,
    onOpenApplications: (AppCategoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, holder) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                holder.startReadCycle()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        // In-app return: activity stayed resumed, so ON_RESUME alone would not fire.
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            holder.startReadCycle()
        }
    }

    val formatting = rememberFigureFormatting()
    val refreshActionLabel = stringResource(R.string.refresh_action)
    val refreshingStatus = stringResource(R.string.refresh_status_refreshing)
    val refreshedStatus = stringResource(R.string.refresh_status_done)

    // Spoken cycle states: "Refreshing figures" while the pass runs, and
    // "Figures refreshed" on each true→false transition of the cycle flag —
    // an idle screen announces nothing.
    var refreshWasRunning by remember { mutableStateOf(false) }
    var announceRefreshed by remember { mutableStateOf(false) }
    LaunchedEffect(holder.isRefreshing) {
        if (holder.isRefreshing) {
            refreshWasRunning = true
            announceRefreshed = false
        } else if (refreshWasRunning) {
            refreshWasRunning = false
            announceRefreshed = true
        }
    }
    val refreshStatusDescription = when {
        holder.isRefreshing -> refreshingStatus
        announceRefreshed -> refreshedStatus
        else -> ""
    }

    PullToRefreshBox(
        isRefreshing = holder.isRefreshing,
        onRefresh = holder::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        // Live-region status node: zero-size, announcement-only (W-5).
        Box(
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = refreshStatusDescription
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .semantics {
                    // Screen-reader trigger path, independent of the touch gesture (W-5).
                    customActions = listOf(
                        CustomAccessibilityAction(refreshActionLabel) {
                            holder.refresh()
                            true
                        },
                    )
                }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FigureRow(
                label = stringResource(R.string.figure_memory),
                state = holder.memory,
                formatValue = formatting::memoryValue,
            )
            FigureRow(
                label = stringResource(R.string.figure_internal_storage),
                state = holder.storage,
                formatValue = formatting::storageValue,
            )
            FigureRow(
                label = stringResource(R.string.figure_battery),
                state = holder.battery,
                formatValue = formatting::batteryValue,
            )
            FigureRow(
                label = stringResource(R.string.figure_processor),
                state = holder.processor,
                formatValue = formatting::processorValue,
            )
            // 004 H-1/H-2: the applications figure as two tappable entries sharing
            // the one state — both placeholders while Loading, both indications
            // while Unavailable, the two counts when Available.
            FigureRow(
                label = stringResource(R.string.figure_user_applications),
                state = holder.applications,
                formatValue = formatting::userApplicationsValue,
                onClick = { onOpenApplications(AppCategoryFilter.USER) },
            )
            FigureRow(
                label = stringResource(R.string.figure_system_applications),
                state = holder.applications,
                formatValue = formatting::systemApplicationsValue,
                onClick = { onOpenApplications(AppCategoryFilter.SYSTEM) },
            )
        }
    }
}

/**
 * One figure group: wrapping label + value row, no fixed heights (A-2), merged
 * screen-reader semantics "label: value-or-indication" (H-8, FR-015). The
 * unavailability indication is announced as text in the value slot —
 * distinctly from any real value, never instead of one that exists.
 * A non-null [onClick] makes the row tappable with row-level clickable
 * semantics (004 H-2).
 */
@Composable
private fun <T> FigureRow(
    label: String,
    state: FigureUiState<T>,
    formatValue: (T) -> String,
    onClick: (() -> Unit)? = null,
) {
    val placeholder = stringResource(R.string.figure_placeholder)
    val unavailable = stringResource(R.string.figure_unavailable)
    val valueText = when (state) {
        is FigureUiState.Available -> formatValue(state.value)
        FigureUiState.Loading -> placeholder
        FigureUiState.Unavailable -> unavailable
    }
    val valueColor = when (state) {
        FigureUiState.Unavailable -> MaterialTheme.colorScheme.error
        FigureUiState.Loading -> MaterialTheme.colorScheme.onSurfaceVariant
        is FigureUiState.Available -> MaterialTheme.colorScheme.onSurface
    }
    val contentDescription = stringResource(R.string.figure_content_description, label, valueText)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
