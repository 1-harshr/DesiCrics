package com.harsh.desicrics.ui.text
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import com.harsh.desicrics.util.nowMillis
import com.harsh.desicrics.resources.*
import androidx.compose.runtime.Composable

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** "23/11 16:30" in the phone's time zone: numbers only, so it reads the same in any language. */
private val DayAndTime = LocalDateTime.Format {
    day()
    char('/')
    monthNumber()
    char(' ')
    hour()
    char(':')
    minute()
}

@OptIn(ExperimentalTime::class)
fun formatDayAndTime(epochMillis: Long): String =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault()).format(DayAndTime)

/**
 * A moment from a date picker's day (Material 3 gives UTC midnight) and a clock time, both meant in the
 * phone's time zone.
 */
@OptIn(ExperimentalTime::class)
fun epochMillisAt(dayUtcMillis: Long, hour: Int, minute: Int): Long {
    val day = Instant.fromEpochMilliseconds(dayUtcMillis).toLocalDateTime(TimeZone.UTC).date
    return LocalDateTime(day, LocalTime(hour, minute)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}

/**
 * When something happened, the way people say it: "Just now", "12 min ago", "3 h ago", "Yesterday", and the
 * day and time beyond that. [now] is passed in so tests and previews stay stable.
 */
@Composable
fun relativeTime(epochMillis: Long, now: Long = nowMillis()): String {
    val minutes = (now - epochMillis) / 60_000
    return when {
        minutes < 1 -> stringResource(Res.string.time_just_now)
        minutes < 60 -> pluralStringResource(Res.plurals.time_minutes_ago, minutes.toInt(), minutes.toInt())
        minutes < 24 * 60 -> pluralStringResource(Res.plurals.time_hours_ago, (minutes / 60).toInt(), (minutes / 60).toInt())
        minutes < 48 * 60 -> stringResource(Res.string.time_yesterday)
        else -> formatDayAndTime(epochMillis)
    }
}

/** The day something happened: "Today", "Yesterday", or "16/11". */
@OptIn(ExperimentalTime::class)
@Composable
fun dayLabel(epochMillis: Long, now: Long = nowMillis()): String {
    val zone = TimeZone.currentSystemDefault()
    val day = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).date
    val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
    return when (today.toEpochDays() - day.toEpochDays()) {
        0L -> stringResource(Res.string.time_today)
        1L -> stringResource(Res.string.time_yesterday)
        else -> formatDayAndTime(epochMillis).substringBefore(' ')
    }
}
