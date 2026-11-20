package com.harsh.desicrics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.harsh.desicrics.data.ThemePreference
import com.harsh.desicrics.designsystem.theme.DesiThemes
import com.russhwolf.settings.SharedPreferencesSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The picked theme survives a restart, and a saved theme that no longer ships falls back to Classic. */
@RunWith(AndroidJUnit4::class)
class ThemePreferenceTest {

    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("theme_test", Context.MODE_PRIVATE)
    private fun preference() = ThemePreference(SharedPreferencesSettings(prefs))

    @Test
    fun startsOnTheDefault() {
        prefs.edit().clear().commit()
        assertEquals(DesiThemes.default, preference().theme.value)
    }

    @Test
    fun remembersThePickAcrossInstances() {
        prefs.edit().clear().commit()
        val picked = DesiThemes.all.last()
        preference().select(picked)
        assertEquals(picked, preference().theme.value)
    }

    @Test
    fun aRetiredThemeFallsBackToTheDefault() {
        prefs.edit().clear().putString("theme_id", "retired_theme").commit()
        assertEquals(DesiThemes.default, preference().theme.value)
    }
}
