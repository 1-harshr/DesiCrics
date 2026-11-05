package com.harsh.desicrics.util

import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Two decimal places, since `String.format` isn't available in common code. */
fun Double.format2(): String {
    val hundredths = (this * 100).roundToInt()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}
