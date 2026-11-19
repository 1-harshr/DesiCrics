package com.harsh.desicrics.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.theme.DesiTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Immutable
data class NavTab(val label: String, val icon: DrawableResource, val selectedIcon: DrawableResource)

/** The app's bottom tabs: outlined icons, filled and green on a soft pill when selected. */
@Composable
fun DesiNavigationBar(tabs: List<NavTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = DesiTheme.colors
    Column(modifier) {
        HorizontalDivider(color = colors.line.copy(alpha = 0.7f))
        NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selected
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(index) },
                    icon = { Icon(painterResource(if (isSelected) tab.selectedIcon else tab.icon), contentDescription = null) },
                    label = {
                        Text(tab.label, style = DesiTheme.type.caption.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium))
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.brand,
                        selectedTextColor = colors.ink,
                        indicatorColor = colors.brandSoft,
                        unselectedIconColor = colors.muted,
                        unselectedTextColor = colors.muted,
                    ),
                )
            }
        }
    }
}
