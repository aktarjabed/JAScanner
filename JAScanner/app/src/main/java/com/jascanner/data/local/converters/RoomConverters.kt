package com.jascanner.data.local.converters

import android.graphics.PointF
import android.graphics.RectF
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RoomConverters {

    private val gson = Gson()

    @TypeConverter
    fun fromRectF(rectF: RectF?): String? {
        return rectF?.let { "${it.left},${it.top},${it.right},${it.bottom}" }
    }

    @TypeConverter
    fun toRectF(value: String?): RectF? {
        return value?.split(',')?.map { it.toFloat() }?.let {
            RectF(it[0], it[1], it[2], it[3])
        }
    }

    @TypeConverter
    fun fromPointFList(points: List<PointF>?): String? {
        return gson.toJson(points)
    }

    @TypeConverter
    fun toPointFList(json: String?): List<PointF>? {
        val type = object : TypeToken<List<PointF>>() {}.type
        return gson.fromJson(json, type)
    }

    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.joinToString(",")
    }

    @TypeConverter
    fun toStringList(data: String?): List<String>? {
        return data?.split(",")
    }
}