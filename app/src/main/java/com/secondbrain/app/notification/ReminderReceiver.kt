package com.secondbrain.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.secondbrain.app.data.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Pengingat"

        sendNotification(context, message, reminderId.toInt())

        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.get(context).reminderDao().markSent(reminderId)
        }
    }

    private fun sendNotification(context: Context, message: String, id: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

    companion object {
        const val CHANNEL_ID = "secondbrain_reminders"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_MESSAGE = "message"
    }
}
