package com.example.chat.global.message;

import com.example.chat.auth.ChatUserPrincipal;
import com.example.chat.global.message.dto.MessagePageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat/rooms/{roomId}/messages")
@RequiredArgsConstructor
public class MessageHistoryController {

    private final ChatMessageService chatMessageService;

    @GetMapping
    public MessagePageResponse history(
            @PathVariable Long roomId,
            @RequestParam(required = false)Long before,
            @RequestParam(defaultValue="50")int size,
            @AuthenticationPrincipal ChatUserPrincipal principal
            ){
        return chatMessageService.history(roomId, principal, before, size);
    }
}
