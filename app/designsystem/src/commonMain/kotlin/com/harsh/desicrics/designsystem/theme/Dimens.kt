package com.harsh.desicrics.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The 4dp spacing scale. Use these for every margin, padding and gap. */
@Immutable
object DesiSpacing {
    val s1: Dp = 4.dp
    val s2: Dp = 8.dp
    val s3: Dp = 12.dp
    val s4: Dp = 16.dp
    val s5: Dp = 20.dp
    val s6: Dp = 24.dp
    val s8: Dp = 32.dp
    val s12: Dp = 48.dp

    /** Side margin of every screen. */
    val screen: Dp = s4
}

@Immutable
object DesiRadius {
    val sm: RoundedCornerShape = RoundedCornerShape(6.dp)
    val md: RoundedCornerShape = RoundedCornerShape(12.dp)
    val lg: RoundedCornerShape = RoundedCornerShape(20.dp)
    val full: RoundedCornerShape = CircleShape
}

/** Minimum sizes for things people tap. */
@Immutable
object DesiSize {
    val touchTarget: Dp = 48.dp
    val scoringTarget: Dp = 64.dp
    val button: Dp = 52.dp
    val ballChip: Dp = 32.dp
    val listRow: Dp = 56.dp
    val listRowWithAvatar: Dp = 72.dp
    val icon: Dp = 24.dp
    val iconSmall: Dp = 20.dp
    val iconLarge: Dp = 32.dp
}

@Immutable
object DesiMotion {
    const val QUICK_MS: Int = 100
    const val BASE_MS: Int = 200
    const val SCORE_MS: Int = 300
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}
