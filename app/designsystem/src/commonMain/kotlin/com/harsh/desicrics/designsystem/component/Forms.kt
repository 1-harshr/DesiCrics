package com.harsh.desicrics.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.resources.Res
import com.harsh.desicrics.designsystem.resources.ds_back
import com.harsh.desicrics.designsystem.theme.DesiTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A fixed `brandStrong` strip the height of the status bar. Put it above a scrolling screen that starts
 * with a [BrandHeader], so the light status bar icons stay readable once the header scrolls away.
 */
@Composable
fun BrandStatusBar() {
    Spacer(Modifier.fillMaxWidth().background(DesiTheme.colors.brandStrong).windowInsetsTopHeight(WindowInsets.statusBars))
}

/**
 * The green header at the top of a form screen: optional back button, a large title and a subtitle.
 * [content] goes underneath (a live preview card, say); the bottom [overhang] of it hangs below the
 * green onto the page. Pair with [BrandStatusBar], which supplies the status bar inset.
 */
@Composable
fun BrandHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    overhang: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = DesiTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind { drawRect(colors.brandStrong, size = Size(size.width, size.height - overhang.toPx())) }
            .padding(horizontal = DesiTheme.space.screen)
            .padding(top = if (onBack == null) DesiTheme.space.s8 else DesiTheme.space.s2, bottom = if (overhang == 0.dp) DesiTheme.space.s6 else 0.dp),
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = -DesiTheme.space.s3)) {
                Icon(painterResource(DesiIcons.Back), stringResource(Res.string.ds_back), tint = colors.onBrandStrong)
            }
        }
        Text(title, style = DesiTheme.type.scoreLg, color = colors.onBrandStrong)
        Text(subtitle, style = DesiTheme.type.bodyLg, color = colors.onBrandStrong.copy(alpha = 0.78f))
        content()
    }
}

/**
 * A whole number picked with − and + buttons around a big value, e.g. overs per innings. [label] sits
 * under the number ("overs"). The buttons disable at the ends of [range].
 */
@Composable
fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
    range: IntRange = 1..50,
) {
    val colors = DesiTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(DesiTheme.radius.lg)
            .background(colors.surface)
            .border(1.dp, colors.line, DesiTheme.radius.lg)
            .padding(DesiTheme.space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(DesiIcons.Remove, decreaseLabel, enabled = value > range.first) { onValueChange(value - 1) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", style = DesiTheme.type.scoreLg, color = colors.ink)
            Text(label, style = DesiTheme.type.caption, color = colors.muted)
        }
        StepButton(DesiIcons.Add, increaseLabel, enabled = value < range.last) { onValueChange(value + 1) }
    }
}

@Composable
private fun StepButton(icon: DrawableResource, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = DesiTheme.colors
    Box(
        Modifier
            .size(DesiTheme.size.touchTarget)
            .clip(DesiTheme.radius.full)
            .background(if (enabled) colors.brandSoft else colors.sunk)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = if (enabled) colors.brand else colors.muted)
    }
}

/** A chip with a remove button, for items the user added to a list (players in a squad). */
@Composable
fun RemovableChip(text: String, removeLabel: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DesiTheme.colors
    Row(
        modifier
            .clip(DesiTheme.radius.full)
            .background(colors.brandSoft)
            .padding(start = DesiTheme.space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = DesiTheme.type.body, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier
                .padding(start = 2.dp)
                .size(36.dp)
                .clip(DesiTheme.radius.full)
                .clickable(role = Role.Button, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(DesiIcons.Close), contentDescription = removeLabel, tint = colors.muted, modifier = Modifier.size(18.dp))
        }
    }
}
