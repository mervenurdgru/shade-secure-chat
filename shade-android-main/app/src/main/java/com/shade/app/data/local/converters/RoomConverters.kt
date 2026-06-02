package com.shade.app.data.local.converters

import androidx.room.TypeConverter
import com.shade.app.data.local.entities.MessageStatus
import com.shade.app.data.local.entities.MessageType

class RoomConverters {
    @TypeConverter
    fun fromMessageType(value: MessageType) = value.name
    @TypeConverter
    fun toMessageType(value: String) = MessageType.valueOf(value)

    @TypeConverter
    fun fromMessageStatus(value: MessageStatus) = value.name
    @TypeConverter
    fun toMessageStatus(value: String) = MessageStatus.valueOf(value)
}