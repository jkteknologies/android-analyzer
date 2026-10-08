package com.jkteknologies.resourceradar.ui.help

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.jkteknologies.resourceradar.R
import com.jkteknologies.resourceradar.ui.openUrl

/** Fixed contact targets (006 FR-011, data-model §3). */
private const val LINKEDIN_URL = "https://www.linkedin.com/in/jurijskolomijecs/"
private const val WEBSITE_URL = "https://jkteknologies.com/"

/**
 * The Help destination (006 FR-006..FR-012, contracts N-1): one scrollable
 * column with exactly five titled sections in spec order — About,
 * Limitations, Per-app memory, Contacts, License (the AGPL text from
 * `res/raw/license.txt`, read once at entering composition, R-07). Contacts
 * rows open their fixed https targets via the shared opener; a missing
 * handler surfaces the shell's auto-dismissing snackbar through
 * [onLinkUnavailable] (FR-016, contracts L-3). No search, index, or
 * collapsible sections (spec Assumption). Sp-scaled text, wrapping, no
 * fixed heights.
 */
@Composable
fun HelpScreen(onLinkUnavailable: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val license = remember {
        context.resources.openRawResource(R.raw.license).bufferedReader().use { it.readText() }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        HelpSection(title = stringResource(R.string.help_title_about)) {
            BodyText(stringResource(R.string.help_about))
        }
        HelpSection(title = stringResource(R.string.help_title_limitations)) {
            BodyText(stringResource(R.string.help_limitations))
        }
        HelpSection(title = stringResource(R.string.help_title_per_app_memory)) {
            BodyText(stringResource(R.string.help_per_app_memory))
        }
        HelpSection(title = stringResource(R.string.help_title_contacts)) {
            ContactRow(
                label = stringResource(R.string.contact_linkedin),
                url = LINKEDIN_URL,
                context = context,
                onLinkUnavailable = onLinkUnavailable,
            )
            ContactRow(
                label = stringResource(R.string.contact_website),
                url = WEBSITE_URL,
                context = context,
                onLinkUnavailable = onLinkUnavailable,
            )
        }
        HelpSection(title = stringResource(R.string.help_title_license)) {
            BodyText(license)
        }
    }
}

/** One titled section: heading-annotated title above its body content. */
@Composable
private fun HelpSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier
            .padding(top = 24.dp)
            .semantics { heading() },
    )
    content()
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One full-row tappable contact link (FR-011); failure → snackbar (FR-016). */
@Composable
private fun ContactRow(
    label: String,
    url: String,
    context: Context,
    onLinkUnavailable: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!openUrl(context, url)) onLinkUnavailable() }
            .padding(vertical = 12.dp),
    )
}
