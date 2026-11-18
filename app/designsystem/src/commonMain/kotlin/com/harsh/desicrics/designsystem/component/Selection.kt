package com.harsh.desicrics.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Two to four mutually exclusive options in one pill, e.g. right- or left-hand bat. The selected option
 * sits on a raised thumb that slides between segments.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DesiTheme.colors
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(DesiTheme.size.touchTarget)
            .clip(DesiTheme.radius.full)
            .background(colors.sunk)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val thumbOffset by animateDpAsState(segment * selectedIndex, tween(DesiTheme.motion.BASE_MS, easing = DesiTheme.motion.standard))
        Box(
            Modifier
                .offset(x = thumbOffset)
                .width(segment)
                .fillMaxHeight()
                .clip(DesiTheme.radius.full)
                .background(colors.surface)
                .border(1.dp, colors.line, DesiTheme.radius.full),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val textColor by animateColorAsState(if (isSelected) colors.ink else colors.muted, tween(DesiTheme.motion.BASE_MS))
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(DesiTheme.radius.full)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = if (isSelected) DesiTheme.type.bodyStrong else DesiTheme.type.body,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = DesiTheme.space.s2),
                    )
                }
            }
        }
    }
}

/**
 * A large tappable choice with an icon, title and one-line description, for picks that deserve more than
 * a chip (playing role). Lay several out in a grid; the selected card gets the brand outline and a tick.
 */
@Composable
fun OptionCard(
    title: String,
    description: String,
    icon: DrawableResource,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DesiTheme.colors
    val border by animateColorAsState(if (selected) colors.brand else colors.line, tween(DesiTheme.motion.BASE_MS))
    val background by animateColorAsState(if (selected) colors.brandSoft else colors.surface, tween(DesiTheme.motion.BASE_MS))
    Column(
        modifier
            .clip(DesiTheme.radius.lg)
            .background(background)
            .border(if (selected) 2.dp else 1.dp, border, DesiTheme.radius.lg)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(DesiTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s1),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = DesiTheme.space.s2), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(40.dp).clip(DesiTheme.radius.md).background(if (selected) colors.surface else colors.brandSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = colors.brand, modifier = Modifier.size(DesiTheme.size.icon))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopEnd) {
                if (selected) {
                    Icon(painterResource(DesiIcons.CheckCircle), contentDescription = null, tint = colors.brand, modifier = Modifier.size(DesiTheme.size.iconSmall))
                }
            }
        }
        Text(title, style = DesiTheme.type.bodyStrong, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(description, style = DesiTheme.type.caption, color = colors.muted, maxLines = 2, minLines = 2)
    }
}

/**
 * A player's identity at a glance: avatar, name, a role line and style tags. Used as the live preview while
 * someone sets up their profile, and on profile screens. [name] falls back to [namePlaceholder] in muted ink.
 */
@Composable
fun PlayerCard(
    name: String,
    namePlaceholder: String,
    subtitle: String,
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = DesiTheme.colors
    Column(
        modifier
            .clip(DesiTheme.radius.lg)
            .background(colors.surface)
            .border(1.dp, colors.line, DesiTheme.radius.lg)
            .padding(DesiTheme.space.s5),
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s4)) {
            Avatar(name, size = 56)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name.ifBlank { namePlaceholder },
                    style = DesiTheme.type.titleLg,
                    color = if (name.isBlank()) colors.muted else colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(subtitle, style = DesiTheme.type.body, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
                tags.forEach { tag ->
                    Text(
                        tag,
                        style = DesiTheme.type.caption,
                        color = colors.brand,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(DesiTheme.radius.full)
                            .background(colors.brandSoft)
                            .padding(horizontal = DesiTheme.space.s3, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
