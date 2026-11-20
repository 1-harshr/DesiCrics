package com.harsh.desicrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.harsh.desicrics.api.CitySuggestion
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.ui.components.CitySuggestions
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The city field while suggestions are showing, light and dark, saved to app/shared/screenshots/city_field_*.png. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class CityFieldScreenshotTest {

    @Test
    fun suggestions() {
        System.setProperty("roborazzi.test.record", "true")
        val cities = listOf(CitySuggestion("Aurangabad", "Maharashtra"), CitySuggestion("Aurangabad", "Bihar"), CitySuggestion("Auraiya", "Uttar Pradesh"))
        listOf(false, true).forEach { dark ->
            captureRoboImage("screenshots/city_field_${if (dark) "dark" else "light"}.png") {
                DesiTheme(darkTheme = dark, reduceMotion = true) {
                    Column(Modifier.background(DesiTheme.colors.bg).padding(DesiTheme.space.screen), verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
                        DesiTextField("Aura", {}, "City", leadingIcon = DesiIcons.Location)
                        CitySuggestions(cities, onPick = {})
                    }
                }
            }
        }
    }
}
