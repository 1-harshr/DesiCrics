package com.harsh.desicrics.ui.text

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
