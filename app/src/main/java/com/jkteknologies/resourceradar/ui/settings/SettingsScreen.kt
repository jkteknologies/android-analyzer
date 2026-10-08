package com.jkteknologies.androidanalyzer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jkteknologies.androidanalyzer.R
import com.jkteknologies.androidanalyzer.domain.RefreshMode
import com.jkteknologies.androidanalyzer.domain.ThemePreference

/**
 * The settings screen (002 S-1..S-6; 004 contracts/refresh-mode.md S-1..S-4):
 * exactly TWO sections — Theme (Light / Dark / System default, untouched from
 * 002) and Automatic refresh (On demand / Every 30 seconds / Every minute /
 * Every 5 minutes). A tap raises the selection callback; the host persists
 * first then applies it immediately. Rows expose selectable semantics with
 * announced state (S-6); text is sp-scaled and rows wrap — no fixed heights.
 */
@Composable
fun SettingsScreen(
    selectedPreference: ThemePreference,
    onPreferenceSelected: (ThemePreference) -> Unit,
    selectedRefreshMode: RefreshMode,
    onRefreshModeSelected: (RefreshMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.theme_label),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )
        ThemeOptionRow(
            label = stringResource(R.string.theme_light),
            selected = selectedPreference == ThemePreference.LIGHT,
            onClick = { onPreferenceSelected(ThemePreference.LIGHT) },
        )
        ThemeOptionRow(
            label = stringResource(R.string.theme_dark),
            selected = selectedPreference == ThemePreference.DARK,
            onClick = { onPreferenceSelected(ThemePreference.DARK) },
        )
        ThemeOptionRow(
            label = stringResource(R.string.theme_system),
            selected = selectedPreference == ThemePreference.SYSTEM,
            onClick = { onPreferenceSelected(ThemePreference.SYSTEM) },
        )
        Text(
            text = stringResource(R.string.refresh_mode_label),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )
        ThemeOptionRow(
            label = stringResource(R.string.refresh_mode_on_demand),
            selected = selectedRefreshMode == RefreshMode.ON_DEMAND,
            onClick = { onRefreshModeSelected(RefreshMode.ON_DEMAND) },
        )
        ThemeOptionRow(
            label = stringResource(R.string.refresh_mode_30s),
            selected = selectedRefreshMode == RefreshMode.THIRTY_SECONDS,
            onClick = { onRefreshModeSelected(RefreshMode.THIRTY_SECONDS) },
        )
        ThemeOptionRow(
            label = stringResource(R.string.refresh_mode_1min),
            selected = selectedRefreshMode == RefreshMode.ONE_MINUTE,
            onClick = { onRefreshModeSelected(RefreshMode.ONE_MINUTE) },
        )
        ThemeOptionRow(
            label = stringResource(R.string.refresh_mode_5min),
            selected = selectedRefreshMode == RefreshMode.FIVE_MINUTES,
            onClick = { onRefreshModeSelected(RefreshMode.FIVE_MINUTES) },
        )
    }
}

/** One selectable option row (S-1/S-6): shared by the theme and refresh sections. */
@Composable
private fun ThemeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
