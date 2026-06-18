package com.secondbrain.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.secondbrain.app.notification.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoroutineScope(Dispatchers.IO).launch {
                ReminderScheduler.scheduleUpcoming(context)
            }
        }
    }
}
