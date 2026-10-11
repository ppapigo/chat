package com.example.chat.global.message;

import com.example.chat.auth.ChatUserPrincipal;
import com.example.chat.global.exception.BusinessException;
import com.example.chat.global.exception.ErrorCode;
import com.example.chat.global.exception.ErrorResponse;
import com.example.chat.global.exception.StompAuthException;
import com.example.chat.global.message.dto.SendMessageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/chat/rooms")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @MessageMapping("/room/{roomId}/message")
    public void send(
            @DestinationVariable Long roomId,
            @Payload SendMessageRequest request,
            Principal principal
            ){
        ChatUserPrincipal sender = ChatUserPrincipal.from(principal)
                .orElseThrow(()->new StompAuthException(ErrorCode.LOGIN_REQUIRED));
        chatMessageService.send(roomId, sender, request.content());
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleBusiness(BusinessException ex){
        return ErrorResponse.of(ex.getErrorCode());
    }
    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleUnexpected(Exception ex){
        return ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
