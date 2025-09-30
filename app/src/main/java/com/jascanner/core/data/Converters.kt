package com.jascanner.core.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jascanner.edit.domain.model.EditablePage

class Converters {
    @TypeConverter
    fun fromEditablePageList(value: List<EditablePage>): String {
        val gson = Gson()
        val type = object : TypeToken<List<EditablePage>>() {}.type
        return gson.toJson(value, type)
    }

    @TypeConverter
    fun toEditablePageList(value: String): List<EditablePage> {
        val gson = Gson()
        val type = object : TypeToken<List<EditablePage>>() {}.type
        return gson.fromJson(value, type)
    }
}