package com.harsh.desicrics.data

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which app the user sees: the minimal one for scoring, or everything. */
enum class AppMode { SIMPLE, FULL }

/**
 * The app mode picked on this phone. Like the theme, it's a device choice kept in local settings. Phones
 * that never picked one stay on [AppMode.FULL], so nobody who already uses the app loses anything.
 */
class ModePreference(private val settings: Settings) {

    private val picked = MutableStateFlow(
        settings.getStringOrNull(KEY)?.let { saved -> AppMode.entries.firstOrNull { it.name == saved } } ?: AppMode.FULL,
    )

    val mode: StateFlow<AppMode> = picked.asStateFlow()

    fun select(mode: AppMode) {
        settings.putString(KEY, mode.name)
        picked.value = mode
    }

    private companion object {
        const val KEY = "app_mode"
    }
}
