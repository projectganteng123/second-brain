package com.secondbrain.app

import android.app.Application
import com.secondbrain.app.data.database.AppDatabase
import com.secondbrain.app.data.repository.NoteRepository

class SecondBrainApp : Application() {

    val database by lazy { AppDatabase.get(this) }
    val repository by lazy {
        NoteRepository(database.noteDao(), database.reminderDao())
    }
}
