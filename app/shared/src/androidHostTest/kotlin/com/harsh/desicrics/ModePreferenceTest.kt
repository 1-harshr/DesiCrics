package com.harsh.desicrics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.harsh.desicrics.data.AppMode
import com.harsh.desicrics.data.ModePreference
import com.russhwolf.settings.SharedPreferencesSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Phones that never picked a mode stay on Full, and a pick survives a restart. */
@RunWith(AndroidJUnit4::class)
class ModePreferenceTest {

    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("mode_test", Context.MODE_PRIVATE)
    private fun preference() = ModePreference(SharedPreferencesSettings(prefs))

    @Test
    fun startsOnFull() {
        prefs.edit().clear().commit()
        assertEquals(AppMode.FULL, preference().mode.value)
    }

    @Test
    fun remembersThePickAcrossInstances() {
        prefs.edit().clear().commit()
        preference().select(AppMode.SIMPLE)
        assertEquals(AppMode.SIMPLE, preference().mode.value)
    }

    @Test
    fun anUnknownValueFallsBackToFull() {
        prefs.edit().clear().putString("app_mode", "LEGACY").commit()
        assertEquals(AppMode.FULL, preference().mode.value)
    }
}
