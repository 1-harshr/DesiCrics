package com.harsh.desicrics.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.resources.Res
import com.harsh.desicrics.designsystem.resources.ds_try_again
import com.harsh.desicrics.designsystem.theme.DesiTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * What a screen shows when its content couldn't load: a drawn mark, a plain-words [title] saying what
 * didn't happen, a [body] saying what to do, and Try again. [icon] is [DesiIcons.CloudOff] for connection
 * trouble and [DesiIcons.SearchOff] when the thing doesn't exist. [secondary] is an optional way out
 * (Back, Sign out). Fills and centres in the space it's given. [compact] draws it smaller on a card, for
 * a section of a page (a tab, a list) rather than the whole screen.
 */
@Composable
fun ErrorState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: DrawableResource = DesiIcons.CloudOff,
    onRetry: (() -> Unit)? = null,
    retrying: Boolean = false,
    secondary: (@Composable () -> Unit)? = null,
    compact: Boolean = false,
) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    val frame = if (compact) {
        modifier.fillMaxWidth().clip(DesiTheme.radius.md).background(colors.surface).border(1.dp, colors.line.copy(alpha = 0.7f), DesiTheme.radius.md)
    } else {
        modifier.fillMaxSize()
    }
    Box(frame, contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp).padding(horizontal = space.s6, vertical = if (compact) space.s5 else space.s8),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            ErrorMark(icon, if (compact) 104.dp else 136.dp)
            Text(
                title,
                style = DesiTheme.type.titleMd,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = if (compact) 0.dp else space.s4).semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(body, style = DesiTheme.type.body, color = colors.muted, textAlign = TextAlign.Center)
            if (onRetry != null) {
                PrimaryButton(
                    stringResource(Res.string.ds_try_again),
                    onClick = onRetry,
                    loading = retrying,
                    leadingIcon = DesiIcons.Refresh,
                    modifier = Modifier.padding(top = if (compact) space.s1 else space.s4).fillMaxWidth(),
                )
            }
            secondary?.invoke()
        }
    }
}

/** The error picture: the icon on a warm disc inside a dashed orbit with a few loose dots, like a ball gone astray. */
@Composable
private fun ErrorMark(icon: DrawableResource, diameter: Dp) {
    val colors = DesiTheme.colors
    val tone = colors.event.extra
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width / 2, size.height / 2)
            drawCircle(tone.copy(alpha = 0.07f), radius = size.minDimension / 2, center = centre)
            drawCircle(
                tone.copy(alpha = 0.35f),
                radius = size.minDimension / 2 - 10.dp.toPx(),
                center = centre,
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))),
            )
            drawCircle(tone.copy(alpha = 0.6f), radius = 4.dp.toPx(), center = Offset(size.width * 0.86f, size.height * 0.24f))
            drawCircle(tone.copy(alpha = 0.35f), radius = 3.dp.toPx(), center = Offset(size.width * 0.12f, size.height * 0.7f))
            drawCircle(tone.copy(alpha = 0.25f), radius = 2.dp.toPx(), center = Offset(size.width * 0.3f, size.height * 0.1f))
        }
        Box(Modifier.size(diameter * 0.56f).clip(DesiTheme.radius.full).background(tone.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = tone, modifier = Modifier.size(diameter * 0.26f))
        }
    }
}

/**
 * A problem that doesn't replace the page: a save that failed, an action the server refused, content that
 * may be out of date. Sits in the flow of the page or sheet. [action] (Try again, Dismiss) is optional.
 * [warning] uses amber instead of red, for states that aren't failures, like being offline.
 */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: DrawableResource = DesiIcons.Error,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    warning: Boolean = false,
) {
    val colors = DesiTheme.colors
    val tone = if (warning) colors.event.extra else colors.event.wicket
    Row(
        modifier
            .fillMaxWidth()
            .clip(DesiTheme.radius.md)
            .background(tone.copy(alpha = 0.08f))
            .border(1.dp, tone.copy(alpha = 0.22f), DesiTheme.radius.md)
            .padding(start = DesiTheme.space.s3, end = DesiTheme.space.s2, top = DesiTheme.space.s3, bottom = DesiTheme.space.s3)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tone, modifier = Modifier.size(DesiTheme.size.iconSmall))
        Column(Modifier.weight(1f)) {
            if (title != null) Text(title, style = DesiTheme.type.bodyStrong, color = colors.ink)
            Text(message, style = if (title != null) DesiTheme.type.caption else DesiTheme.type.body, color = if (title != null) colors.muted else colors.ink)
        }
        if (action != null && onAction != null) {
            Text(
                action,
                style = DesiTheme.type.button,
                color = colors.brand,
                modifier = Modifier.clip(DesiTheme.radius.full).clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = DesiTheme.space.s2, vertical = 6.dp),
            )
        }
    }
}
