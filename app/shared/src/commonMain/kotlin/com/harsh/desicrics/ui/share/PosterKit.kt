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
import androidx.compose.runtime.Immutable
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

/** Mown stripes, like a ground on TV, over whatever's behind. */
@Composable
internal fun Stripes(ink: Color, bands: Int = 6, alpha: Float = 0.05f) {
    Canvas(Modifier.fillMaxSize()) {
        val band = size.width / bands
        for (i in 0 until bands step 2) drawRect(ink.copy(alpha = alpha), Offset(i * band, 0f), Size(band, size.height))
    }
}

/** Page [number] (from 1) of [count], for a poster split over several pictures. */
@Immutable
data class PosterPage(val number: Int, val count: Int)

/** "2/3" in a small pill. */
@Composable
internal fun PageBadge(page: PosterPage?, ink: Color, container: Color) {
    page ?: return
    Box(Modifier.clip(DesiTheme.radius.full).background(container).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(stringResource(Res.string.share_page, page.number, page.count), style = DesiTheme.type.label.sized(12, 16), color = ink)
    }
}

/** A one-line uppercase label in the poster's [style]. */
@Composable
internal fun Eyebrow(text: String, color: Color, style: TextStyle = DesiTheme.type.label.sized(13, 16), modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}
