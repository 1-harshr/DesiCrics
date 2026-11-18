package com.harsh.desicrics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.harsh.desicrics.data.Analytics

/** [Analytics] through Firebase Analytics. Crash reports go to Crashlytics on their own once it's in the build. */
class FirebaseAnalyticsLogger(context: Context) : Analytics {
    private val firebase = FirebaseAnalytics.getInstance(context)

    override fun log(event: String, params: Map<String, String>) {
        firebase.logEvent(event, Bundle().apply { params.forEach { (key, value) -> putString(key, value) } })
    }
}
