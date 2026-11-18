package com.harsh.desicrics

import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.messaging.RemoteMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * The silent live-score messages the server's Notifier sends, delivered to the real PushService, checked
 * against what Android ends up showing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PushServiceTest {

    /** Each test gets a new Application, which starts Koin again; the global one has to go first. */
    @After
    fun tearDown() = stopKoin()

    private val manager get() = ApplicationProvider.getApplicationContext<android.content.Context>().getSystemService(NotificationManager::class.java)

    private fun deliver(data: Map<String, String>) {
        val service = Robolectric.buildService(PushService::class.java).create().get()
        service.onMessageReceived(RemoteMessage.Builder("test@fcm.googleapis.com").setData(data).build())
    }

    private fun live(type: String, score: String) =
        mapOf("type" to type, "matchId" to "m1", "title" to "Strikers vs Royals", "score" to score)

    @Test
    fun liveScoresUpdateOneQuietNotificationAndGoWhenTheMatchEnds() {
        deliver(live("live", "Strikers 36/0 (1.0)"))
        var shown = manager.activeNotifications.filter { it.tag == "m1" }
        assertEquals(1, shown.size)
        val first = shown.single().notification
        assertEquals("live_scores", first.channelId)
        assertEquals("Strikers 36/0 (1.0)", first.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(first.flags and Notification.FLAG_ONGOING_EVENT != 0)
        // The quiet channel: no sound or pop-up for every over.
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel("live_scores").importance)

        // The next over replaces it rather than adding another.
        deliver(live("live", "Strikers 52/1 (2.0)"))
        shown = manager.activeNotifications.filter { it.tag == "m1" }
        assertEquals(1, shown.size)
        assertEquals("Strikers 52/1 (2.0)", shown.single().notification.extras.getString(Notification.EXTRA_TEXT))

        deliver(live("live_ended", "Strikers 140/6 (20.0) · Royals 120/9 (20.0)"))
        assertTrue(manager.activeNotifications.none { it.tag == "m1" })
    }

    @Test
    fun eachMatchHasItsOwnLiveNotification() {
        deliver(live("live", "Strikers 10/0 (1.0)"))
        deliver(live("live", "Kings 8/1 (1.0)").plus("matchId" to "m2"))
        assertEquals(setOf("m1", "m2"), manager.activeNotifications.mapNotNull { it.tag }.toSet())
    }

    @Test
    fun theDefaultChannelExistsBeforeAnyPush() {
        // Created when the app starts, so a push Firebase shows in the background has a proper channel.
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, manager.getNotificationChannel("match_updates").importance)
    }
}
