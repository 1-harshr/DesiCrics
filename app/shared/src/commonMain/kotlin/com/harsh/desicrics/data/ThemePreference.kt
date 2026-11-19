package com.harsh.desicrics.data

import com.harsh.desicrics.designsystem.theme.DesiThemeSpec
import com.harsh.desicrics.designsystem.theme.DesiThemes
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The theme picked on this phone. It's a device choice, not an account one, so it lives in local
 * settings and isn't sent to the server.
 */
class ThemePreference(private val settings: Settings) {

    private val picked = MutableStateFlow(DesiThemes.byId(settings.getStringOrNull(KEY)))

    /** The picked theme, or [DesiThemes.default] if none is saved or the saved one no longer ships. */
    val theme: StateFlow<DesiThemeSpec> = picked.asStateFlow()

    fun select(theme: DesiThemeSpec) {
        settings.putString(KEY, theme.id)
        picked.value = theme
    }

    private companion object {
        const val KEY = "theme_id"
    }
}
