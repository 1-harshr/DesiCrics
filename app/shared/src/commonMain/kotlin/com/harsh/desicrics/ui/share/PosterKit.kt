package com.harsh.desicrics.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.icon.DesiIllustrations
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * Small pieces the tournament posters share: dates as a poster prints them, team marks, the footer and the
 * page badge. Everything takes its colours from the share palette and the theme, never literals.
 */

/** "Sat 22 Nov". */
@Composable
internal fun posterDay(day: LocalDate): String =
    stringResource(
        Res.string.poster_day,
        stringArrayResource(Res.array.poster_weekdays)[day.dayOfWeek.isoDayNumber - 1],
        day.day,
        stringArrayResource(Res.array.poster_months)[day.month.number - 1],
    )

@OptIn(ExperimentalTime::class)
private fun local(epochMillis: Long) = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())

/** "16:30" in the phone's time zone. */
internal fun posterTime(epochMillis: Long): String =
    local(epochMillis).let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" }

/** "Sat 22 Nov · 16:30". */
@Composable
internal fun posterDateTime(epochMillis: Long): String =
    stringResource(Res.string.poster_day_time, posterDay(local(epochMillis).date), posterTime(epochMillis))

/** First letters of the first and last words: "Andheri Strikers" is "AS", "Royals" is "R". */
internal fun posterInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return listOfNotNull(words.firstOrNull(), words.drop(1).lastOrNull()).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
}

/**
 * A team's mark: a disc with its initials (teams have no logos in tournaments yet), optionally ringed.
 */
@Composable
internal fun TeamMark(name: String, size: Dp, container: Color, ink: Color, ring: Color? = null, textSize: Int = (size.value * 0.4f).toInt()) {
    Box(
        Modifier.size(size).clip(DesiTheme.radius.full).background(container)
            .then(if (ring != null) Modifier.border(2.dp, ring, DesiTheme.radius.full) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(posterInitials(name), style = DesiTheme.type.scoreMd.sized(textSize), color = ink, maxLines = 1)
    }
}

/** Mown stripes, like a ground on TV, over whatever's behind. */
@Composable
internal fun Stripes(ink: Color, bands: Int = 6, alpha: Float = 0.05f) {
    Canvas(Modifier.fillMaxSize()) {
        val band = size.width / bands
        for (i in 0 until bands step 2) drawRect(ink.copy(alpha = alpha), Offset(i * band, 0f), Size(band, size.height))
    }
}

/** "2/3" in a small pill. */
@Composable
internal fun PageBadge(page: SharePage?, ink: Color, container: Color) {
    page ?: return
    Box(Modifier.clip(DesiTheme.radius.full).background(container).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(stringResource(Res.string.poster_page, page.number, page.count), style = DesiTheme.type.label.sized(12, 16), color = ink)
    }
}

/** The foot of a poster: the ball mark and DesiCrics, then [trailing] small (the city) on the right. */
@Composable
internal fun PosterFooter(ink: Color, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(DesiIllustrations.BallMark), contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        Text(stringResource(Res.string.app_name).uppercase(), style = DesiTheme.type.label.sized(12, 16), color = ink)
        if (!trailing.isNullOrBlank()) {
            Box(Modifier.weight(1f))
            Text(trailing.uppercase(), style = DesiTheme.type.label.sized(12, 16), color = ink.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A one-line uppercase label in the poster's [style]. */
@Composable
internal fun Eyebrow(text: String, color: Color, style: TextStyle = DesiTheme.type.label.sized(13, 16), modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}
