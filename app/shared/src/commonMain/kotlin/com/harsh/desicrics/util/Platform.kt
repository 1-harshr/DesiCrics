package com.harsh.desicrics.util

import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The running build, from the platform module (Android: BuildConfig), e.g. "1.0 (1) Android 15". Sent with support messages. */
data class AppInfo(val version: String)

@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Two decimal places, since `String.format` isn't available in common code. */
fun Double.format2(): String {
    val hundredths = (this * 100).roundToInt()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}
