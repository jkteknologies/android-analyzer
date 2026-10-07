package com.jkteknologies.androidanalyzer.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Domain rules of the 004 Details inventory (data-model §9): the
 * [InstalledApp] and [AppInventory] factories (V-A1/V-A2) and the category
 * filter (V-A3). Pure Kotlin — no Android imports.
 */
class InstalledAppsTest {

    /**
     * The domain factory sorts with the default-locale Collator; pin the
     * locale so the ordering assertions are deterministic on any JVM.
     */
    private val originalLocale = Locale.getDefault()

    @org.junit.Before
    fun pinLocale() {
        Locale.setDefault(Locale.US)
    }

    @org.junit.After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    private fun app(
        pkg: String,
        name: String,
        classification: AppClassification = AppClassification.USER,
        storageBytes: Long? = null,
    ): InstalledApp = InstalledApp.create(pkg, name, classification, storageBytes)!!

    @Test
    fun `create rejects a blank package name`() { // V-A1
        assertNull(InstalledApp.create(" ", "App", AppClassification.USER))
        assertNull(InstalledApp.create("", "App", AppClassification.USER))
    }

    @Test
    fun `create rejects a blank display name`() { // V-A1
        assertNull(InstalledApp.create("pkg", "", AppClassification.SYSTEM))
        assertNull(InstalledApp.create("pkg", "  ", AppClassification.SYSTEM))
    }

    @Test
    fun `create rejects negative byte values`() { // V-A1
        assertNull(InstalledApp.create("pkg", "App", AppClassification.USER, storageBytes = -1))
        assertNull(InstalledApp.create("pkg", "App", AppClassification.USER, memoryBytes = -1))
    }

    @Test
    fun `create accepts null and zero byte values`() { // V-A1
        assertEquals(
            InstalledApp("pkg", "App", AppClassification.USER, null, null),
            InstalledApp.create("pkg", "App", AppClassification.USER),
        )
        assertEquals(
            InstalledApp("pkg", "App", AppClassification.USER, 0, 0),
            InstalledApp.create("pkg", "App", AppClassification.USER, 0, 0),
        )
    }

    @Test
    fun `inventory create sorts by display name with a primary-strength collator`() { // V-A2
        val inventory = AppInventory.create(
            listOf(app("c", "Zulu"), app("a", "apple"), app("b", "Banana")),
        )!!

        assertEquals(listOf("apple", "Banana", "Zulu"), inventory.apps.map { it.displayName })
    }

    @Test
    fun `inventory create rejects duplicate package names`() { // V-A2
        assertNull(AppInventory.create(listOf(app("pkg", "One"), app("pkg", "Two"))))
    }

    @Test
    fun `inventory create allows the empty list`() { // V-A2
        val empty = AppInventory.create(emptyList())!!

        assertEquals(0, empty.totalCount)
        assertEquals(0, empty.userCount)
        assertEquals(0, empty.systemCount)
    }

    @Test
    fun `inventory derived counts split by classification`() {
        val inventory = AppInventory.create(
            listOf(
                app("a", "A", AppClassification.USER),
                app("b", "B", AppClassification.SYSTEM),
                app("c", "C", AppClassification.USER),
            ),
        )!!

        assertEquals(3, inventory.totalCount)
        assertEquals(2, inventory.userCount)
        assertEquals(1, inventory.systemCount)
    }

    @Test
    fun `filter ALL returns the full sorted list unchanged`() { // V-A3
        val inventory = AppInventory.create(listOf(app("b", "Beta"), app("a", "Alpha")))!!

        assertEquals(inventory.apps, AppCategoryFilter.ALL.apply(inventory))
    }

    @Test
    fun `filter USER and SYSTEM return the matching subsets preserving order`() { // V-A3
        val inventory = AppInventory.create(
            listOf(
                app("u1", "A user", AppClassification.USER),
                app("s1", "B system", AppClassification.SYSTEM),
                app("u2", "C user", AppClassification.USER),
            ),
        )!!

        assertEquals(
            listOf("A user", "C user"),
            AppCategoryFilter.USER.apply(inventory).map { it.displayName },
        )
        assertEquals(
            listOf("B system"),
            AppCategoryFilter.SYSTEM.apply(inventory).map { it.displayName },
        )
    }
}
