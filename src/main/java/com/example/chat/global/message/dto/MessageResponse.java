package com.example.chat.global.message.dto;

import com.example.chat.global.message.ChatMessage;
import com.example.chat.global.message.MessageType;

import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        Long roomId,
        MessageType type,
        Long senderUserId,
        String senderEmail,
        String content,
        LocalDateTime createdAt
) {
    public static MessageResponse from(ChatMessage message){
        return new MessageResponse(
                message.getId(),
                message.getRoom().getId(),
                message.getType(),
                message.getSenderUserId(),
                message.getSenderUsername(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
