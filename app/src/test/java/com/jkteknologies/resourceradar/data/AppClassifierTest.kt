package com.jkteknologies.androidanalyzer.data

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Data-model validation rule V-8 (specs/002-home-screen/data-model.md §7):
 * classification by the single FLAG_SYSTEM bit (FR-005).
 *
 * [ApplicationInfo.FLAG_SYSTEM] is a compile-time `const` — the Kotlin compiler
 * inlines it, so the android.jar stub is never touched at runtime and the test
 * runs on a plain JVM (research.md R-01, R-14).
 */
class AppClassifierTest {

    @Test
    fun `plain flags mean non-system`() {
        assertFalse(isSystemApplication(flags = 0))
    }

    @Test
    fun `FLAG_SYSTEM set means system`() {
        assertTrue(isSystemApplication(flags = ApplicationInfo.FLAG_SYSTEM))
        assertTrue(isSystemApplication(flags = ApplicationInfo.FLAG_SYSTEM or 0x00FF0000))
    }

    @Test
    fun `updated preinstalled app keeps system classification (FR-005)`() {
        val updatedSystemApp = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        assertTrue(isSystemApplication(flags = updatedSystemApp))
    }

    @Test
    fun `the rule is the single FLAG_SYSTEM bit and nothing else`() {
        // Documents the exact predicate: FLAG_UPDATED_SYSTEM_APP alone does not
        // classify as system (the platform always pairs it with FLAG_SYSTEM).
        assertFalse(isSystemApplication(flags = ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
    }
}
