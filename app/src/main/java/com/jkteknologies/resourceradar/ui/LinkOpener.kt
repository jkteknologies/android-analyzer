package com.jkteknologies.resourceradar.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens a URL in the device browser via an implicit ACTION_VIEW intent
 * (006 contracts L-2): no permission and no `<queries>` entry are needed, and
 * the complete failure surface is [ActivityNotFoundException] (L-4) — reported
 * by returning `false` so the caller can show the auto-dismissing snackbar
 * (L-3, FR-016) instead of crashing. Returns `true` when the intent launched.
 */
fun openUrl(context: Context, url: String): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
