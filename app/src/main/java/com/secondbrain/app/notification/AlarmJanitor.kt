package com.secondbrain.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.secondbrain.app.data.database.AppDatabase
import com.secondbrain.app.util.DebugLog
import kotlinx.coroutines.yield

/**
 * Pembersih alarm basi — dijalankan DI LATAR saat app dibuka (dan menumpang worker
 * per jam), BUKAN saat alarm mau berbunyi.
 *
 * Pendaftaran AlarmManager tetap hidup walau baris pengingatnya sudah dihapus
 * (catatan dihapus / diarsipkan / diproses ulang dengan id pengingat baru).
 * Sweep ini menelusuri rentang id secara bertahap dan MENCABUT pendaftaran yang
 * tidak lagi punya baris pengingat aktif di database.
 */
object AlarmJanitor {

    /** Id pengingat lama yang sudah terhapus bisa lebih tinggi dari id tertinggi
     *  yang tersisa — beri margin penelusuran di atasnya. */
    private const val PROBE_MARGIN = 2000L
    private const val MIN_PROBE = 2000L

    suspend fun sweep(context: Context) {
        val dao = AppDatabase.get(context).reminderDao()
        val alive = dao.getAliveIds().toHashSet()
        val maxProbe = maxOf((alive.maxOrNull() ?: 0L) + PROBE_MARGIN, MIN_PROBE)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        var canceled = 0
        for (id in 1..maxProbe) {
            if (id in alive) continue
            // FLAG_NO_CREATE: hanya mengambil pendaftaran yang memang ada, tidak membuat baru
            val pi = PendingIntent.getBroadcast(
                context, id.toInt(),
                Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
                canceled++
            }
            if (id % 200L == 0L) yield()   // bertahap — jangan serakah CPU/binder
        }
        if (canceled > 0) {
            DebugLog.log("Alarm 🧹 sweep", "$canceled pendaftaran alarm basi dicabut (probe 1..$maxProbe)")
        }
    }
}
