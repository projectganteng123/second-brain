package com.secondbrain.app.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.secondbrain.app.data.model.NoteType

/**
 * Gson bersama dengan deserialisasi NoteType yang case-insensitive.
 * AI sering mengembalikan "meeting"/"task" (huruf kecil), sedangkan nama enum huruf besar;
 * tanpa ini Gson gagal mencocokkan dan semua type jatuh ke NOTE.
 */
object GsonProvider {
    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(NoteType::class.java, JsonDeserializer { json, _, _ ->
            val value = json.asString.trim()
            NoteType.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: NoteType.NOTE
        })
        .create()
}
