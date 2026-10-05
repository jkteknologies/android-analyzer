package com.jkteknologies.androidanalyzer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jkteknologies.androidanalyzer.R

/**
 * Settings surface shell (task T023; ui-contracts S-1): titled "Settings",
 * reserving exactly one setting slot for the theme control that US4 (T026)
 * delivers — no other sections, no placeholders, no "coming soon" (edge S-1).
 * The title exposes heading semantics for screen-reader operability (A-1);
 * the body scrolls and wraps — no fixed heights (A-2).
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
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
        // The single setting (theme selector) arrives with US4 — T026.
    }
}
