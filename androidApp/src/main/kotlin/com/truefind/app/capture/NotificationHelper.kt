package com.truefind.app.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.truefind.app.R
import com.truefind.app.settings.MainActivity

object NotificationHelper {

    const val CHANNEL_BUBBLE = "truefind_bubble"
    const val CHANNEL_CAPTURE = "truefind_capture"
    const val NOTIF_ID_BUBBLE = 1001
    const val NOTIF_ID_CAPTURE = 1002

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val bubbleChannel = NotificationChannel(
            CHANNEL_BUBBLE,
            context.getString(R.string.notification_channel_bubble),
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }

        val captureChannel = NotificationChannel(
            CHANNEL_CAPTURE,
            context.getString(R.string.notification_channel_capture),
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }

        nm.createNotificationChannel(bubbleChannel)
        nm.createNotificationChannel(captureChannel)
    }

    fun buildBubbleNotification(context: Context, stopIntent: Intent): Notification {
        ensureChannels(context)
        val openAppPi = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPi = PendingIntent.getService(
            context,
            2,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_BUBBLE)
            .setSmallIcon(R.drawable.ic_truefind_tile)
            .setContentTitle(context.getString(R.string.bubble_notification_title))
            .setContentText(context.getString(R.string.bubble_notification_text))
            .setContentIntent(openAppPi)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, context.getString(R.string.bubble_action_stop), stopPi)
            .build()
    }

    fun buildCaptureNotification(context: Context): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_CAPTURE)
            .setSmallIcon(R.drawable.ic_truefind_tile)
            .setContentTitle(context.getString(R.string.capture_notification_title))
            .setContentText(context.getString(R.string.capture_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
