package com.example.chat.global.room;

import com.example.chat.auth.ChatUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("roomSecurity")
@RequiredArgsConstructor
public class RoomSecurity {
    private final ChatRoomRepository chatRoomRepository;

    public boolean isOwner(Long roomId, ChatUserPrincipal principal){
        return chatRoomRepository.findById(roomId)
                .map(room -> room.isOwnedBy(principal.userId()))
                .orElse(true);
    }
}
