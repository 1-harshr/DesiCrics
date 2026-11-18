package com.harsh.desicrics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.harsh.desicrics.data.AuthRepository
import com.harsh.desicrics.data.SocialRepository
import com.harsh.desicrics.widget.MatchWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Match updates from the server. In the background Android shows them itself; in the foreground this
 * shows them. Tapping one opens the match through the same link handling as a shared /m/ link.
 */
class PushService : FirebaseMessagingService() {

    private val social: SocialRepository by inject()
    private val auth: AuthRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        // Before sign-in there's nobody to register it for; the app registers it after sign-in instead.
        if (auth.currentUser == null) return
        scope.launch { runCatching { social.savePushToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        when (message.data["type"]) {
            "live" -> showLiveScore(message.data).also { refreshWidget() }
            "live_ended" -> message.data["matchId"]?.let { NotificationManagerCompat.from(this).cancel(it, LIVE_ID) }.also { refreshWidget() }
            else -> showUpdate(message)
        }
    }

    private fun showUpdate(message: RemoteMessage) {
        val body = message.notification?.body ?: return
        channel(CHANNEL, R.string.notifications_channel, NotificationManager.IMPORTANCE_DEFAULT)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(message.notification?.title ?: getString(R.string.app_name))
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(openMatch(message.data["matchId"], body.hashCode()))
            .build()
        post(null, body.hashCode(), notification)
    }

    /** One quiet notification per followed match, updated in place each over and wicket until it ends. */
    private fun showLiveScore(data: Map<String, String>) {
        val matchId = data["matchId"] ?: return
        channel(LIVE_CHANNEL, R.string.live_scores_channel, NotificationManager.IMPORTANCE_LOW)
        val notification = NotificationCompat.Builder(this, LIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(data["title"] ?: getString(R.string.app_name))
            .setContentText(data["score"])
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            // Each update renews this; a match abandoned without a result stops showing after a while.
            .setTimeoutAfter(LIVE_TIMEOUT_MS)
            .setContentIntent(openMatch(matchId, matchId.hashCode()))
            .build()
        post(matchId, LIVE_ID, notification)
    }

    private fun channel(id: String, name: Int, importance: Int) {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(id, getString(name), importance))
    }

    /** Opens the match through the same link handling as a shared /m/ link. */
    private fun openMatch(matchId: String?, requestCode: Int): PendingIntent {
        val open = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            if (matchId != null) data = Uri.parse("${BuildConfig.PUBLIC_URL.trimEnd('/')}/m/$matchId")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(this, requestCode, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun post(tag: String?, id: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) runCatching { manager.notify(tag, id, notification) }
    }

    private fun refreshWidget() {
        scope.launch { runCatching { MatchWidget.refresh(applicationContext) } }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "match_updates"
        const val LIVE_CHANNEL = "live_scores"
        /** Live scores are told apart by their match ID tag; the ID itself is shared. */
        const val LIVE_ID = 1
        const val LIVE_TIMEOUT_MS = 3 * 60 * 60 * 1000L
    }
}
