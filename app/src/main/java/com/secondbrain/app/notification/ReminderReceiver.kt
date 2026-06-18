package com.secondbrain.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.secondbrain.app.MainActivity
import com.secondbrain.app.data.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Pengingat"
        val isAlarm = intent.getBooleanExtra(EXTRA_IS_ALARM, false)

        sendNotification(context, message, reminderId.toInt(), isAlarm)

        if (reminderId >= 0) CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.get(context).reminderDao().markSent(reminderId)
        }
    }

    private fun sendNotification(context: Context, message: String, id: Int, isAlarm: Boolean) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (isAlarm) {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val channel = NotificationChannel(CHANNEL_ALARM, "Alarm pengingat", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
                setSound(alarmSound, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
            manager.createNotificationChannel(channel)

            val fullScreenIntent = Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val fullScreenPi = PendingIntent.getActivity(
                context, id, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(context, CHANNEL_ALARM)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ Second Brain")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSound(alarmSound)
                .setAutoCancel(true)
                .setFullScreenIntent(fullScreenPi, true)
                .build()
            manager.notify(id, notif)
        } else {
            val channel = NotificationChannel(CHANNEL_ID, "Pengingat", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)

            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Second Brain")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            manager.notify(id, notif)
        }
    }

    companion object {
        const val CHANNEL_ID = "secondbrain_reminders"
        const val CHANNEL_ALARM = "secondbrain_alarms"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_IS_ALARM = "is_alarm"
    }
}
