package com.mazesys.zerobite.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mazesys.zerobite.MainActivity
import com.mazesys.zerobite.R
import com.mazesys.zerobite.ui.brand.BrandMarkColors

/** Channels and posting for every notification the app shows. */
object Notifications {
    const val CHANNEL_EXPIRY = "expiry"
    const val EXTRA_ITEM_ID = "com.mazesys.zerobite.extra.ITEM_ID"

    fun createChannels(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_EXPIRY, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Expiry reminders")
                .setDescription("A heads-up before food expires, on the day, and if it slips past.")
                .build(),
        )
    }

    /** True when the app may show notifications (runtime permission on Android 13+, and not blocked). */
    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Posts (or replaces) the notification identified by `tag`; tapping it opens `itemId` if given. */
    @SuppressLint("MissingPermission") // checked by canPost
    fun post(context: Context, tag: String, title: String, body: String, itemId: String?) {
        if (!canPost(context)) return
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { if (itemId != null) putExtra(EXTRA_ITEM_ID, itemId) }
        val tap = PendingIntent.getActivity(context, tag.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_EXPIRY)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BrandMarkColors.red.toArgb())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(tag, 0, notification)
    }
}
