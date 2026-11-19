package com.harsh.desicrics.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.harsh.desicrics.designsystem.resources.Res
import com.harsh.desicrics.designsystem.resources.barlow_condensed_bold
import com.harsh.desicrics.designsystem.resources.barlow_condensed_semibold
import com.harsh.desicrics.designsystem.resources.figtree_variable
import com.harsh.desicrics.designsystem.resources.jetbrains_mono_variable
import org.jetbrains.compose.resources.Font

/** Fixed-width digits so changing scores and table columns don't shift. */
private const val TABULAR = "tnum"

@Immutable
data class DesiTypography(
    val scoreXl: TextStyle,
    val scoreLg: TextStyle,
    val scoreMd: TextStyle,
    val titleLg: TextStyle,
    val titleMd: TextStyle,
    val bodyLg: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val button: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val data: TextStyle,
)

@Composable
private fun displayFamily() = FontFamily(
    Font(Res.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(Res.font.barlow_condensed_bold, FontWeight.Bold),
)

@Composable
private fun bodyFamily() = FontFamily(
    Font(Res.font.figtree_variable, FontWeight.Normal),
    Font(Res.font.figtree_variable, FontWeight.Medium),
    Font(Res.font.figtree_variable, FontWeight.SemiBold),
    Font(Res.font.figtree_variable, FontWeight.Bold),
)

@Composable
private fun monoFamily() = FontFamily(
    Font(Res.font.jetbrains_mono_variable, FontWeight.Medium),
)

/** Lines sit centred in their line height, so fixed line heights keep list rows even. */
private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

@Composable
fun desiTypography(): DesiTypography {
    val display = displayFamily()
    val body = bodyFamily()
    val mono = monoFamily()
    fun style(family: FontFamily, size: Int, lineHeight: Int, weight: FontWeight, tabular: Boolean = false) = TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = weight,
        fontFeatureSettings = if (tabular) TABULAR else null,
        lineHeightStyle = Centered,
    )
    return DesiTypography(
        scoreXl = style(display, 56, 56, FontWeight.Bold, tabular = true),
        scoreLg = style(display, 40, 40, FontWeight.Bold, tabular = true),
        scoreMd = style(display, 24, 28, FontWeight.Bold, tabular = true),
        titleLg = style(display, 36, 40, FontWeight.Bold),
        titleMd = style(display, 24, 28, FontWeight.SemiBold),
        bodyLg = style(body, 17, 26, FontWeight.Normal),
        body = style(body, 15, 22, FontWeight.Normal),
        bodyStrong = style(body, 15, 22, FontWeight.SemiBold),
        button = style(body, 15, 20, FontWeight.SemiBold),
        label = style(body, 12, 16, FontWeight.SemiBold).copy(letterSpacing = 0.1.em),
        caption = style(body, 13, 18, FontWeight.Normal),
        data = style(mono, 13, 18, FontWeight.Medium, tabular = true),
    )
}
