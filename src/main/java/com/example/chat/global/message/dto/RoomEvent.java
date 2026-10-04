package com.example.chat.global.message.dto;

import com.example.chat.global.room.dto.RoomResponse;

public record RoomEvent(
        RoomEventType type,
        RoomResponse room
) {
}
