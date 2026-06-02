package com.shade.app.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.shade.app.R
import com.shade.app.domain.usecase.message.FetchInboxUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class FetchMessagesWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val fetchInboxUseCase: FetchInboxUseCase
) : CoroutineWorker(context, params) {


    companion object {
        private const val FETCH_NOTIFICATION_ID = 9999
        private const val TAG = "FetchMessageWorker"
    }
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Draining inbox...")
            fetchInboxUseCase()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed: ${e.message}")
            Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, "shade_messages")
            .setSmallIcon(R.drawable.ic_stat_shade)
            .setContentTitle("Shade")
            .setContentText("Mesajlar alınıyor...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                FETCH_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(FETCH_NOTIFICATION_ID, notification)
        }
    }
}