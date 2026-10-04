package com.example.chat.global.message;

import com.example.chat.global.message.dto.MessageResponse;
import com.example.chat.global.message.dto.RoomEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatMessagePublisher {
    public static final String LOBBY_TOPIC = "/topic/rooms";
    public final SimpMessagingTemplate messagingTemplate;

    public static String roomTopic(Long roomId){
        return "/topic/room/" +roomId;
    }

    public void publisheMessage(MessageResponse message){
        messagingTemplate.convertAndSend(roomTopic(message.roomId()),message);
    }

    public void publishRoomEvent(RoomEvent event){
        messagingTemplate.convertAndSend(LOBBY_TOPIC, event);
    }
}
