package com.example.chat.global.message.dto;

import java.util.List;

public record MessagePageResponse(
        List<MessageResponse> messages,
        boolean hasMore,
        Long nextBefore
) {

}
