package com.shade.app.firebase

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.shade.app.R
import com.shade.app.security.KeyVaultManager
import com.shade.app.worker.FetchMessagesWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.random.Random

class ShadeFirebaseMessagingService : FirebaseMessagingService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface FirebaseServiceEntryPoint {
        fun keyVaultManager(): KeyVaultManager
    }

    private fun getEntryPoint(): FirebaseServiceEntryPoint {
        return EntryPointAccessors.fromApplication(
            applicationContext,
            FirebaseServiceEntryPoint::class.java
        )
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
        serviceScope.launch {
            getEntryPoint().keyVaultManager().saveFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val type = message.data["type"]

        when (type) {
            "NEW_ENCRYPTED_MESSAGE" -> {
                Log.d("FCM", "Wake-up signal received, enqueuing fetch worker...")
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val request = OneTimeWorkRequestBuilder<FetchMessagesWorker>()
                    .setConstraints(constraints)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
                // KEEP: rapid FCM must not cancel a running/queued fetch (REPLACE did that).
                WorkManager.getInstance(applicationContext)
                    .enqueueUniqueWork(
                        "fetch_undelivered_messages",
                        ExistingWorkPolicy.KEEP,
                        request
                    )
            }
            else -> {
                val title = message.notification?.title ?: "Shade"
                val body = message.notification?.body ?: message.data["message"] ?: "New Message"
                showNotification(title, body)
            }
        }
    }
    private fun showNotification(title: String, body: String) {
        val channelId = "shade_notifications"

        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Shade Notifications",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_stat_shade)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(Random.nextInt(), notification)
    }
}
