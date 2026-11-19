package com.harsh.desicrics.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.theme.DesiTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Immutable
data class NavTab(val label: String, val icon: DrawableResource, val selectedIcon: DrawableResource)

private val BarHeight = 64.dp
private val BarElevation = 6.dp

/**
 * Room a tab's root screen leaves below its content, on top of the system bar inset, so the floating
 * bar doesn't cover the end of it. Zero everywhere else.
 */
val LocalNavBarClearance = staticCompositionLocalOf { 0.dp }

/** What [LocalNavBarClearance] is set to under the floating bar: the bar, its margin and a gap. */
val NavBarClearance: Dp = BarHeight + 24.dp

/**
 * The app's bottom tabs, Material 3 Expressive style: a pill floating over the content, above the
 * system bar. Outlined icons, filled and green on a soft pill when selected.
 */
@Composable
fun DesiNavigationBar(tabs: List<NavTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    val shape = DesiTheme.radius.full
    ShortNavigationBar(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = space.s6, end = space.s6, bottom = space.s4)
            .shadow(BarElevation, shape)
            .border(1.dp, colors.line.copy(alpha = 0.7f), shape),
        containerColor = colors.surface,
        windowInsets = WindowInsets(0),
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selected
            ShortNavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(index) },
                icon = { Icon(painterResource(if (isSelected) tab.selectedIcon else tab.icon), contentDescription = null) },
                label = {
                    Text(tab.label, style = DesiTheme.type.caption.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium))
                },
                colors = ShortNavigationBarItemDefaults.colors(
                    selectedIconColor = colors.brand,
                    selectedTextColor = colors.ink,
                    selectedIndicatorColor = colors.brandSoft,
                    unselectedIconColor = colors.muted,
                    unselectedTextColor = colors.muted,
                ),
            )
        }
    }
}
