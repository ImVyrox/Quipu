package com.quipu.app.reminders

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.quipu.app.R
import com.quipu.app.data.NotificationSound
import com.quipu.app.data.SoundMode

/** Canal de avisos y publicación de notificaciones. */
object QuipuNotifications {
    private const val CHANNEL_PREFIX = "reminders_v"
    const val TEST_NOTIFICATION_ID = 1

    fun canNotify(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Garantiza que exista el canal con el sonido y la vibración actuales y borra los
     * canales de versiones anteriores. Devuelve el id del canal vigente.
     */
    fun ensureChannel(context: Context, sound: NotificationSound): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = CHANNEL_PREFIX + sound.channelVersion
        manager.notificationChannels
            .filter { it.id.startsWith(CHANNEL_PREFIX) && it.id != id }
            .forEach { manager.deleteNotificationChannel(it.id) }
        if (manager.getNotificationChannel(id) == null) {
            val channel = NotificationChannel(id, "Recordatorios", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tareas y pagos por vencer"
                enableVibration(sound.vibrate)
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                when (sound.mode) {
                    SoundMode.DEFAULT -> setSound(Settings.System.DEFAULT_NOTIFICATION_URI, attributes)
                    SoundMode.SILENT -> setSound(null, null)
                    SoundMode.CUSTOM -> setSound(
                        sound.customUri?.let(Uri::parse) ?: Settings.System.DEFAULT_NOTIFICATION_URI,
                        attributes,
                    )
                }
            }
            manager.createNotificationChannel(channel)
        }
        return id
    }

    /** Publica un aviso si el usuario permite notificaciones; si no, no hace nada. */
    @SuppressLint("MissingPermission") // Se comprueba con canNotify().
    fun post(
        context: Context,
        sound: NotificationSound,
        id: Int,
        title: String,
        text: String,
        finance: Boolean,
    ) {
        if (!canNotify(context)) return
        val channelId = ensureChannel(context, sound)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = open?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(if (finance) R.drawable.ic_nav_finance else R.drawable.ic_nav_tasks)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
