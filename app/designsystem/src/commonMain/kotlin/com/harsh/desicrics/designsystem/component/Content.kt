package com.harsh.desicrics.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.resources.Res
import com.harsh.desicrics.designsystem.resources.ds_live
import com.harsh.desicrics.designsystem.theme.DesiTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** A bordered, flat surface. Cards never get a shadow. */
@Composable
fun DesiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DesiTheme.colors
    Column(
        modifier
            .clip(DesiTheme.radius.md)
            .background(colors.surface)
            .border(1.dp, colors.line, DesiTheme.radius.md)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(DesiTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3),
        content = content,
    )
}

/** Red dot plus "LIVE". */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    val color = DesiTheme.colors.event.wicket
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).clip(DesiTheme.radius.full).background(color))
        Text(stringResource(Res.string.ds_live), style = DesiTheme.type.label, color = color)
    }
}

@Immutable
data class TeamLine(val name: String, val score: String? = null, val overs: String? = null, val batting: Boolean = false)

/**
 * A match in a feed. [status] is the top-left slot ([LiveBadge] or a date label), [meta] the top-right
 * text (format and venue), and [footer] a line such as "Strikers need 27 off 20 balls".
 */
@Composable
fun MatchCard(
    teams: List<TeamLine>,
    modifier: Modifier = Modifier,
    status: @Composable () -> Unit = {},
    meta: String? = null,
    footer: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = DesiTheme.colors
    DesiCard(modifier, onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { status() }
            if (meta != null) Text(meta, style = DesiTheme.type.data, color = colors.muted, maxLines = 1)
        }
        teams.forEach { team ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    team.name,
                    style = DesiTheme.type.bodyStrong,
                    color = if (team.batting) colors.ink else colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (team.score != null) {
                    Text(team.score, style = DesiTheme.type.scoreMd, color = if (team.batting) colors.ink else colors.muted)
                }
                if (team.overs != null) {
                    Text(
                        team.overs,
                        style = DesiTheme.type.data,
                        color = colors.muted,
                        modifier = Modifier.padding(start = DesiTheme.space.s1, bottom = 2.dp),
                    )
                }
            }
        }
        if (footer != null) Text(footer, style = DesiTheme.type.data, color = colors.muted)
    }
}

/**
 * A player in a list: avatar, name and a secondary line (role or styles). [avatar] lets the caller load
 * a photo; without one, the first letter of [name] is shown.
 */
@Composable
fun PlayerRow(
    name: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    avatar: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val colors = DesiTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = DesiTheme.size.listRowWithAvatar)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = DesiTheme.space.screen, vertical = DesiTheme.space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3),
    ) {
        Avatar(name, avatar)
        Column(Modifier.weight(1f)) {
            Text(name, style = DesiTheme.type.bodyStrong, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = DesiTheme.type.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

/** A 40dp circle with a photo, the name's first letter, or a person icon. */
@Composable
fun Avatar(name: String, image: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier, size: Int = 40) {
    val colors = DesiTheme.colors
    Box(
        modifier.size(size.dp).clip(DesiTheme.radius.full).background(colors.brandSoft).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        val initial = name.trim().firstOrNull()?.uppercaseChar()
        when {
            image != null -> image()
            initial != null -> Text(initial.toString(), style = DesiTheme.type.titleMd, color = colors.brand)
            else -> Icon(painterResource(DesiIcons.Person), contentDescription = null, tint = colors.brand)
        }
    }
}

/** Shows how many scored balls are still waiting to reach the server. Hidden when nothing is pending. */
@Composable
fun SyncIndicator(pendingCount: Int, label: String, modifier: Modifier = Modifier) {
    if (pendingCount <= 0) return
    val colors = DesiTheme.colors
    Row(
        modifier
            .clip(DesiTheme.radius.full)
            .background(colors.sunk)
            .padding(horizontal = DesiTheme.space.s3, vertical = DesiTheme.space.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s1),
    ) {
        Icon(painterResource(DesiIcons.Sync), contentDescription = null, tint = colors.event.extra, modifier = Modifier.size(DesiTheme.size.iconSmall))
        Text(label, style = DesiTheme.type.caption, color = colors.ink)
    }
}

/** An uppercase section label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = DesiTheme.type.label, color = DesiTheme.colors.muted, modifier = modifier)
}
