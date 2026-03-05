package com.sohan.diutransportschedule.db

import android.util.Log
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object JsonConverters {
    private val gson = Gson()
    private val type = object : TypeToken<List<String>>() {}.type

    @TypeConverter
    fun listToJson(list: List<String>?): String = gson.toJson(list ?: emptyList<String>())

    @TypeConverter
    fun jsonToList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<String>>(json, type) ?: emptyList()
        } catch (t: Throwable) {
            Log.e("JsonConverters", "Malformed JSON in jsonToList, returning emptyList()", t)
            emptyList()
        }
    }
}
