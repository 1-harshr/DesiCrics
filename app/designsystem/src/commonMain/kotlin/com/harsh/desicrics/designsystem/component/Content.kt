package com.harsh.desicrics.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
import com.harsh.desicrics.designsystem.resources.ds_batting
import com.harsh.desicrics.designsystem.resources.ds_innings_overs
import com.harsh.desicrics.designsystem.resources.ds_yet_to_bat
import com.harsh.desicrics.designsystem.resources.ds_live
import com.harsh.desicrics.designsystem.theme.DesiTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** A quiet surface that lets the content carry the hierarchy. */
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
            .border(1.dp, colors.line.copy(alpha = 0.7f), DesiTheme.radius.md)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(DesiTheme.space.s5),
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
    val live = teams.any { it.batting }
    Column(
        modifier.fillMaxWidth().clip(DesiTheme.radius.md)
            .background(colors.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(DesiTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { status() }
            if (meta != null) Text(meta, style = DesiTheme.type.caption, color = colors.muted)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 260.dp * LocalDensity.current.fontScale) {
                Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
                    teams.forEach { team ->
                        Column {
                            TeamName(team)
                            TeamScore(team, showPending = live)
                        }
                    }
                }
            } else {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s4)) {
                        teams.forEach { TeamName(it, Modifier.weight(1f)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s4)) {
                        teams.forEach { TeamScore(it, live, Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (footer != null) {
            androidx.compose.material3.HorizontalDivider(color = colors.line)
            Text(footer, style = DesiTheme.type.body, color = colors.ink)
        }
    }
}

@Composable
private fun TeamName(team: TeamLine, modifier: Modifier = Modifier) {
    Text(team.name, modifier = modifier, style = DesiTheme.type.bodyLg, fontWeight = FontWeight.Bold, color = DesiTheme.colors.ink)
}

@Composable
private fun TeamScore(team: TeamLine, showPending: Boolean, modifier: Modifier = Modifier) {
    val colors = DesiTheme.colors
    Column(modifier) {
        if (team.score != null) {
            Text(team.score, style = DesiTheme.type.scoreLg, color = colors.ink)
            val detail = listOfNotNull(
                team.overs?.let { stringResource(Res.string.ds_innings_overs, it) },
                if (team.batting) stringResource(Res.string.ds_batting) else null,
            ).joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, style = DesiTheme.type.caption, color = colors.muted)
        } else if (showPending) {
            Text(stringResource(Res.string.ds_yet_to_bat), style = DesiTheme.type.caption, color = colors.muted)
        }
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

/** A circle (40dp by default) with a photo, the name's initials ("RI" for Rohan Iyer), or a person icon. */
@Composable
fun Avatar(name: String, image: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier, size: Int = 40) {
    val colors = DesiTheme.colors
    Box(
        modifier.size(size.dp).clip(DesiTheme.radius.full).background(colors.brandSoft).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        val initials = initials(name)
        when {
            image != null -> image()
            initials.isNotEmpty() -> Text(
                initials,
                style = if (size >= 56) DesiTheme.type.scoreMd else DesiTheme.type.titleMd,
                color = colors.brand,
            )
            else -> Icon(painterResource(DesiIcons.Person), contentDescription = null, tint = colors.brand)
        }
    }
}

/** First letters of the first and last words: "Rohan Iyer" → "RI", "Zoya" → "Z". */
private fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return listOfNotNull(words.firstOrNull(), words.drop(1).lastOrNull())
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
}

/** An uppercase section label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = DesiTheme.type.label, color = DesiTheme.colors.muted, modifier = modifier)
}
