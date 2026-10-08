package com.jkteknologies.resourceradar.domain

import com.jkteknologies.resourceradar.data.ThemePreferenceStore
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Theme preference resolution and persistence fallback (task T017; data-model
 * §4, validation rule V-10): effective resolution per FR-006/FR-010, missing or
 * corrupt persisted value falls back to SYSTEM (FR-009), and the store contract
 * round-trips a selection. The store is faked — `SharedPreferences` itself is
 * an Android class kept out of the JVM test path (R-07, R-14); the platform
 * adapter delegates the fallback logic to [ThemePreference.fromPersisted],
 * which is what these tests pin.
 */
class ThemePreferenceTest {

    // ------------------------------------------------- effective resolution

    @Test
    fun `LIGHT preference resolves to Light regardless of system mode`() {
        assertEquals(EffectiveTheme.LIGHT, effectiveTheme(ThemePreference.LIGHT, systemInDarkMode = true))
        assertEquals(EffectiveTheme.LIGHT, effectiveTheme(ThemePreference.LIGHT, systemInDarkMode = false))
    }

    @Test
    fun `DARK preference resolves to Dark regardless of system mode`() {
        assertEquals(EffectiveTheme.DARK, effectiveTheme(ThemePreference.DARK, systemInDarkMode = true))
        assertEquals(EffectiveTheme.DARK, effectiveTheme(ThemePreference.DARK, systemInDarkMode = false))
    }

    @Test
    fun `SYSTEM follows the device mode in both branches`() {
        assertEquals(EffectiveTheme.DARK, effectiveTheme(ThemePreference.SYSTEM, systemInDarkMode = true))
        assertEquals(EffectiveTheme.LIGHT, effectiveTheme(ThemePreference.SYSTEM, systemInDarkMode = false))
    }

    // ------------------------------------------------- persisted fallback (V-10)

    @Test
    fun `missing persisted value falls back to SYSTEM`() {
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromPersisted(null))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromPersisted(""))
    }

    @Test
    fun `corrupt persisted value falls back to SYSTEM`() {
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromPersisted("corrupt"))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromPersisted("light"))
    }

    @Test
    fun `valid persisted enum names round-trip`() {
        for (preference in ThemePreference.entries) {
            assertEquals(preference, ThemePreference.fromPersisted(preference.name))
        }
    }

    // ------------------------------------------------- store contract (fake)

    /** In-memory fake of the ThemePreferenceStore seam (contracts/device-readers.md). */
    private class FakeThemePreferenceStore : ThemePreferenceStore {
        var persisted: String? = null

        override fun load(): ThemePreference = ThemePreference.fromPersisted(persisted)

        override fun save(preference: ThemePreference) {
            persisted = preference.name
        }
    }

    @Test
    fun `store contract round-trips a saved selection`() {
        val store = FakeThemePreferenceStore()
        assertEquals(ThemePreference.SYSTEM, store.load()) // nothing persisted yet

        store.save(ThemePreference.DARK)
        assertEquals(ThemePreference.DARK, store.load())
        assertEquals("DARK", store.persisted) // stored as the enum name (R-07)
    }

    @Test
    fun `store save is idempotent`() {
        val store = FakeThemePreferenceStore()
        store.save(ThemePreference.LIGHT)
        store.save(ThemePreference.LIGHT)
        assertEquals(ThemePreference.LIGHT, store.load())
    }
}
