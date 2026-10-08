package com.jkteknologies.resourceradar.ui.help

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SC-005 / R-07: the license shown in Help must be the repo `LICENSE`
 * verbatim. `res/raw/license.txt` is a plain repo file, so byte-equality is
 * asserted with filesystem reads — no Robolectric, no new dependency.
 */
class LicenseVerbatimTest {

    @Test
    fun `raw license resource is byte-identical to repo LICENSE`() {
        val repo = repoRoot()
        val license = File(repo, "LICENSE")
        val raw = File(repo, "app/src/main/res/raw/license.txt")
        assertTrue("LICENSE missing at ${license.absolutePath}", license.isFile)
        assertTrue("res/raw/license.txt missing at ${raw.absolutePath}", raw.isFile)
        assertArrayEquals(license.readBytes(), raw.readBytes())
    }

    /** Walks up from the JVM working directory until the repo root (has `gradlew`). */
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null && !File(dir, "gradlew").exists()) dir = dir.parentFile
        checkNotNull(dir) { "repo root not found above ${System.getProperty("user.dir")}" }
        return dir
    }
}
