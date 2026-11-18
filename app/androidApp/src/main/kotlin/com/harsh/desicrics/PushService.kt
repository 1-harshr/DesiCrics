package com.harsh.desicrics

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
        val body = message.notification?.body ?: return
        val matchId = message.data["matchId"]
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notifications_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            if (matchId != null) data = Uri.parse("${BuildConfig.PUBLIC_URL.trimEnd('/')}/m/$matchId")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(message.notification?.title ?: getString(R.string.app_name))
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(this, body.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(this).notify(body.hashCode(), notification) }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "match_updates"
    }
}
