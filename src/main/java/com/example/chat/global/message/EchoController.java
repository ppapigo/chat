package com.example.chat.global.message;

import com.example.chat.global.message.dto.EchoRequest;
import com.example.chat.global.message.dto.EchoResponse;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;

@Controller
public class EchoController {

    @MessageMapping("/echo")
    @SendTo("/topic/echo")
    public EchoResponse echo(EchoRequest request, Principal principal){
        return  new EchoResponse(principal.getName(), request.content(), Instant.now());
    }
}
