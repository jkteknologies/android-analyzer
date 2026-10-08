package com.jkteknologies.androidanalyzer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.jkteknologies.androidanalyzer.domain.EffectiveTheme
import com.jkteknologies.androidanalyzer.domain.ThemePreference
import com.jkteknologies.androidanalyzer.domain.effectiveTheme

/**
 * App-wide theming (task T020; ui-contracts T-1..T-4): static Material 3
 * light/dark palettes — no dynamic color in this feature (R-06). The scheme is
 * selected by [effectiveTheme] from the persisted [preference] and
 * [isSystemInDarkTheme], so a system dark-mode switch recomposes this call and
 * applies the new scheme live without restart (FR-006); after a backgrounded
 * switch the correct theme is present on the next resume (SC-004).
 */
@Composable
fun AppTheme(
    preference: ThemePreference,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (effectiveTheme(preference, isSystemInDarkTheme())) {
        EffectiveTheme.LIGHT -> lightColorScheme()
        EffectiveTheme.DARK -> darkColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
