package com.secondbrain.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.secondbrain.app.MainActivity
import com.secondbrain.app.data.database.AppDatabase
import com.secondbrain.app.util.DebugLog

object ReminderScheduler {

    suspend fun scheduleUpcoming(context: Context) {
        val dao = AppDatabase.get(context).reminderDao()
        val now = System.currentTimeMillis()
        // jendela lebih panjang agar reminder beberapa hari ke depan ikut terjadwal
        val window = now + 7L * 24 * 60 * 60 * 1000L
        val upcoming = dao.getUpcoming(now, window)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            alarmManager.canScheduleExactAlarms() else true

        DebugLog.log("Alarm ⏰ jadwal", "${upcoming.size} pengingat, exactDiizinkan=$canExact")
        var needExactPermission = false

        for (reminder in upcoming) {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
                putExtra(ReminderReceiver.EXTRA_MESSAGE, reminder.message)
                putExtra(ReminderReceiver.EXTRA_IS_ALARM, reminder.isAlarm)
            }
            val pi = PendingIntent.getBroadcast(
                context, reminder.id.toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (reminder.isAlarm) {
                    // Alarm: paling andal, kebal Doze, tidak butuh izin exact khusus
                    val showIntent = PendingIntent.getActivity(
                        context, reminder.id.toInt(),
                        Intent(context, MainActivity::class.java)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(reminder.remindAt, showIntent), pi
                    )
                } else if (canExact) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.remindAt, pi)
                } else {
                    // TANPA jalur tidak-presisi: pengingat presisi butuh izin — beri tahu pengguna.
                    needExactPermission = true
                    DebugLog.log("Alarm ✕ izin", "id=${reminder.id} dilewati: izin exact alarm belum diberikan")
                }
            } catch (e: SecurityException) {
                DebugLog.log("Alarm ✕", "Gagal jadwalkan id=${reminder.id}: ${e.message}")
                needExactPermission = true
            }
        }

        if (needExactPermission) notifyPermissionNeeded(context)
    }

    /** Notifikasi (id tetap → tidak menumpuk) yang mengantar pengguna ke setelan izin. */
    private fun notifyPermissionNeeded(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channel = android.app.NotificationChannel(
            ReminderReceiver.CHANNEL_ID, "Pengingat",
            android.app.NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)

        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
        } else Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, PERMISSION_NOTIF_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = androidx.core.app.NotificationCompat.Builder(context, ReminderReceiver.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Pengingat butuh izin")
            .setContentText("Beri izin \"Alarm & pengingat\" agar notifikasi acara tampil tepat waktu. Ketuk untuk membuka setelan.")
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle())
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        manager.notify(PERMISSION_NOTIF_ID, notif)
    }

    private const val PERMISSION_NOTIF_ID = 990001
}
