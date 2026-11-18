package com.harsh.desicrics.live

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream

/** Firebase Cloud Messaging, with a service account key read from [serviceAccountPath]. */
class FcmPushSender(serviceAccountPath: String, projectId: String) : PushSender {

    private val messaging: FirebaseMessaging = run {
        val options = FirebaseOptions.builder()
            .setCredentials(FileInputStream(serviceAccountPath).use { GoogleCredentials.fromStream(it) })
            .setProjectId(projectId)
            .build()
        FirebaseMessaging.getInstance(FirebaseApp.initializeApp(options, "desicrics-push"))
    }

    override suspend fun send(tokens: List<String>, title: String, body: String, data: Map<String, String>): List<String> =
        withContext(Dispatchers.IO) {
            // FCM takes up to 500 tokens per call.
            tokens.chunked(500).flatMap { batch ->
                val message = MulticastMessage.builder()
                    .addAllTokens(batch)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .putAllData(data)
                    .build()
                val response = messaging.sendEachForMulticast(message)
                response.responses.mapIndexedNotNull { i, sent ->
                    batch[i].takeIf { !sent.isSuccessful && sent.exception?.messagingErrorCode == MessagingErrorCode.UNREGISTERED }
                }
            }
        }
}
