package com.harsh.desicrics.ui.system

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.harsh.desicrics.designsystem.theme.DesiTheme

@Composable
actual fun LightStatusBarIcons() {
    val view = LocalView.current
    val darkTheme = DesiTheme.colors.isDark
    if (view.isInEditMode) return
    DisposableEffect(view, darkTheme) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = false
        // Back to the theme's default (dark icons on the light theme) when the screen leaves.
        onDispose { controller.isAppearanceLightStatusBars = !darkTheme }
    }
}
