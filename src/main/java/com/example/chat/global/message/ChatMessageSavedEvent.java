package com.example.chat.global.message;

import com.example.chat.global.message.dto.MessageResponse;

/**
 * Immutable message snapshot captured while the transaction is active.
 * A listener broadcasts it only after the enclosing transaction commits.
 */
public record ChatMessageSavedEvent(MessageResponse message) {
}
