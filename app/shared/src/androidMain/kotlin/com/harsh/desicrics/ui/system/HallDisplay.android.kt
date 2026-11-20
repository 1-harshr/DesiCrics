package com.harsh.desicrics.ui.system

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
actual fun HallDisplay(landscape: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view, landscape) {
        val activity = view.context as? Activity ?: return@DisposableEffect onDispose {}
        // After the turn the rebuilt activity already asks for landscape; the app itself follows the sensor.
        val before = activity.requestedOrientation.takeIf { it != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (landscape) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Turning sideways rebuilds the activity, which disposes this; only a real exit puts it back.
            if (landscape && !activity.isChangingConfigurations) activity.requestedOrientation = before
        }
    }
}
