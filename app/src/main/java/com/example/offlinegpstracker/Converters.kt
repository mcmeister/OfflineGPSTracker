package com.example.offlinegpstracker

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    @TypeConverter
    fun fromPhotoPathsList(value: List<String>?): String {
        return Gson().toJson(value) // Converts List<String> to JSON String
    }

    @TypeConverter
    fun toPhotoPathsList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType) ?: emptyList() // Converts JSON String back to List<String>
    }
}
