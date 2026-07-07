package com.secondbrain.app

import android.app.Application
import com.secondbrain.app.data.database.AppDatabase
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.notification.ReminderWorker

class SecondBrainApp : Application() {

    val database by lazy { AppDatabase.get(this) }
    val repository by lazy {
        NoteRepository(database.noteDao(), database.reminderDao(), appContext = this)
    }

    override fun onCreate() {
        super.onCreate()
        ReminderWorker.enqueuePeriodic(this)
    }
}
