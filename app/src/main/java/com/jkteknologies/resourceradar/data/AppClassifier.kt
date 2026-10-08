package com.jkteknologies.resourceradar.data

import android.content.pm.ApplicationInfo

/**
 * System-application classification (FR-005, data-model §3, research.md R-01).
 *
 * The rule is the single [ApplicationInfo.FLAG_SYSTEM] bit: an application is a
 * system application iff that bit is set. Updated preinstalled apps keep
 * `FLAG_SYSTEM` (the platform pairs it with `FLAG_UPDATED_SYSTEM_APP`), so they
 * stay in the system count.
 *
 * [ApplicationInfo.FLAG_SYSTEM] is a compile-time `const` — inlined by the
 * compiler, so this stays JVM-test-safe (R-01, R-14).
 */
fun isSystemApplication(flags: Int): Boolean = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
