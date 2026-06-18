package com.secondbrain.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.secondbrain.app.data.database.AppDatabase

object ReminderScheduler {

    suspend fun scheduleUpcoming(context: Context) {
        val dao = AppDatabase.get(context).reminderDao()
        val now = System.currentTimeMillis()
        val in24h = now + 24 * 60 * 60 * 1000L
        val upcoming = dao.getUpcoming(now, in24h)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for (reminder in upcoming) {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
                putExtra(ReminderReceiver.EXTRA_MESSAGE, reminder.message)
                putExtra(ReminderReceiver.EXTRA_IS_ALARM, reminder.isAlarm)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                reminder.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.remindAt, pi)
        }
    }
}
