package com.jkteknologies.resourceradar.ui.details

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.jkteknologies.resourceradar.R
import com.jkteknologies.resourceradar.data.shizuku.ShizukuAccess
import com.jkteknologies.resourceradar.domain.AppCategoryFilter
import com.jkteknologies.resourceradar.domain.AppClassification
import com.jkteknologies.resourceradar.domain.FigureUiState
import com.jkteknologies.resourceradar.domain.ShizukuAccessState
import com.jkteknologies.resourceradar.domain.InstalledApp

/**
 * The per-application Details screen (004 contracts/details-screen.md
 * D-1..D-5, G-1..G-2, R-1..R-2, P-1..P-3).
 *
 * Body: a three-option single-select filter (All / User / System) above a
 * `LazyColumn` of application rows — display name, system/user mark, and the
 * storage + memory figure pair with the distinct not-available indication for
 * `null` values (D-3). Loading and Unavailable keep the filter usable (D-5);
 * a filter matching zero apps renders the explanatory empty state (D-4).
 *
 * The usage-access grant hint renders only while `usageAccessGranted == false`
 * (G-1); tapping a row opens the system's app-info page for that package
 * (R-1); the whole scrollable list sits inside a `PullToRefreshBox` with the
 * 003 accessibility pair — custom refresh action + polite live region (P-2) —
 * and lifecycle triggers mirror Home: `ON_RESUME` and entering composition
 * while resumed start the presentation cycle; dispose shuts the executor
 * down (P-3). Sp-scaled text, wrapping rows, no fixed heights.
 */
@Composable
fun DetailsScreen(holder: DetailsStateHolder, modifier: Modifier = Modifier) {
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

    val context = LocalContext.current
    val formatBytes = remember(context) {
        { bytes: Long -> Formatter.formatShortFileSize(context, bytes) }
    }
    val openAppInfo = remember(context) {
        { packageName: String ->
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null),
                ),
            )
        }
    }
    val openShizuku = remember(context) {
        {
            context.packageManager
                .getLaunchIntentForPackage(ShizukuAccess.SHIZUKU_PACKAGE)
                ?.let(context::startActivity)
            Unit // not installed / no launcher entry: the row simply does nothing (G-3)
        }
    }

    // 003 a11y pair reused verbatim (P-2): screen-reader refresh trigger path
    // plus spoken "Refreshing figures" / "Figures refreshed" cycle states.
    val refreshActionLabel = stringResource(R.string.refresh_action)
    val refreshingStatus = stringResource(R.string.refresh_status_refreshing)
    val refreshedStatus = stringResource(R.string.refresh_status_done)
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
        // Live-region status node: zero-size, announcement-only (003 W-5).
        Box(
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = refreshStatusDescription
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(refreshActionLabel) {
                            holder.refresh()
                            true
                        },
                    )
                }
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.details_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (holder.usageAccessGranted == false) {
                GrantHintRow()
            }
            holder.shizukuAccess?.let { state ->
                ShizukuGuidanceRow(
                    state = state,
                    onOpenShizuku = openShizuku,
                    onRequestAuthorization = holder::requestAuthorization,
                )
            }
            FilterRow(holder)
            when (val state = holder.inventory) {
                FigureUiState.Loading -> BodyPlaceholder(
                    text = stringResource(R.string.figure_placeholder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FigureUiState.Unavailable -> BodyPlaceholder(
                    text = stringResource(R.string.figure_unavailable),
                    color = MaterialTheme.colorScheme.error,
                )
                is FigureUiState.Available -> {
                    val shown = holder.filter.apply(state.value)
                    if (shown.isEmpty()) {
                        BodyPlaceholder(
                            text = stringResource(R.string.details_empty, filterLabel(holder.filter)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(shown, key = { it.packageName }) { app ->
                                AppRow(
                                    app = app,
                                    formatBytes = formatBytes,
                                    onOpen = openAppInfo,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Neutral centered body surface for Loading / Unavailable / empty-filter states (D-4/D-5). */
@Composable
private fun ColumnScope.BodyPlaceholder(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
    }
}

/**
 * The usage-access hint (G-1): one row above the list naming what is missing
 * plus the button opening `Settings.ACTION_USAGE_ACCESS_SETTINGS`. The list,
 * marks, filter, and counts stay fully usable while ungranted.
 */
@Composable
private fun GrantHintRow() {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.usage_access_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
        ) {
            Text(stringResource(R.string.usage_access_grant))
        }
    }
}

/**
 * The Shizuku guidance row (005 contracts/details-guidance.md G-1..G-6): one
 * row below the usage-access hint and above the filter naming the current
 * state in plain language, with the matching action — "Open Shizuku" when it
 * is stopped, "Allow access" (Shizuku's own dialog) when awaiting
 * authorization; text only otherwise, one quiet line when authorized. The
 * text is a polite live region so state changes are announced without focus
 * changes (G-5); the row never blocks the list, filter, or figures (G-6).
 * Same layout as [GrantHintRow] (text `weight(1f)` + optional button,
 * sp-scaled, no fixed heights).
 */
@Composable
private fun ShizukuGuidanceRow(
    state: ShizukuAccessState,
    onOpenShizuku: () -> Unit,
    onRequestAuthorization: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = guidanceText(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        when (state) {
            ShizukuAccessState.NOT_RUNNING -> Button(onClick = onOpenShizuku) {
                Text(stringResource(R.string.shizuku_open))
            }
            ShizukuAccessState.AWAITING_AUTHORIZATION -> Button(onClick = onRequestAuthorization) {
                Text(stringResource(R.string.shizuku_allow))
            }
            ShizukuAccessState.NOT_INSTALLED, ShizukuAccessState.OUTDATED, ShizukuAccessState.AUTHORIZED -> Unit
        }
    }
}

@Composable
private fun guidanceText(state: ShizukuAccessState): String = stringResource(
    when (state) {
        ShizukuAccessState.NOT_INSTALLED -> R.string.shizuku_hint_not_installed
        ShizukuAccessState.NOT_RUNNING -> R.string.shizuku_hint_not_running
        ShizukuAccessState.OUTDATED -> R.string.shizuku_hint_outdated
        ShizukuAccessState.AWAITING_AUTHORIZATION -> R.string.shizuku_hint_awaiting
        ShizukuAccessState.AUTHORIZED -> R.string.shizuku_hint_authorized
    },
)

/** The three-option single-select filter above the list (D-2, FR-005). */
@Composable
private fun FilterRow(holder: DetailsStateHolder) {
    val options = AppCategoryFilter.entries
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = holder.filter == option,
                onClick = { holder.setFilter(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(filterLabel(option))
            }
        }
    }
}

@Composable
private fun filterLabel(filter: AppCategoryFilter): String = stringResource(
    when (filter) {
        AppCategoryFilter.ALL -> R.string.filter_all
        AppCategoryFilter.USER -> R.string.filter_user
        AppCategoryFilter.SYSTEM -> R.string.filter_system
    },
)

/**
 * One application row (D-1/D-3, R-1): display name + system/user mark on the
 * first line, "Storage X · Memory Y" on the second — a `null` byte value
 * renders the distinct not-available indication, never zero. Tapping opens
 * the system's app-info page for the row's package.
 */
@Composable
private fun AppRow(
    app: InstalledApp,
    formatBytes: (Long) -> String,
    onOpen: (String) -> Unit,
) {
    val unavailable = stringResource(R.string.figure_unavailable)
    val storageText = stringResource(
        R.string.app_storage_value,
        app.storageBytes?.let(formatBytes) ?: unavailable,
    )
    val memoryText = stringResource(
        R.string.app_memory_value,
        app.memoryBytes?.let(formatBytes) ?: unavailable,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(app.packageName) }
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = app.displayName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = markLabel(app.classification),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "$storageText · $memoryText",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun markLabel(classification: AppClassification): String = stringResource(
    when (classification) {
        AppClassification.SYSTEM -> R.string.app_mark_system
        AppClassification.USER -> R.string.app_mark_user
    },
)
