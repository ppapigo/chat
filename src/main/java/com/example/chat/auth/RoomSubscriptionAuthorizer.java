package com.example.chat.auth;

import com.example.chat.global.exception.ErrorCode;
import com.example.chat.global.exception.StompAuthException;
import com.example.chat.global.room.ChatRoomRepository;
import com.example.chat.global.room.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class RoomSubscriptionAuthorizer {
    private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/room/(\\d+)$");
    private final ChatRoomRepository rooms;
    private final RoomMemberRepository members;

    public void authorizeSend(String destination) {
        if (!"/app/echo".equals(destination)
                && (destination == null || !destination.matches("^/app/room/\\d+/message$")))
            throw new StompAuthException(ErrorCode.ACCESS_DENIED);
    }

    public void authorize(String destination, ChatUserPrincipal user) {
        if ("/topic/rooms".equals(destination) || "/user/queue/errors".equals(destination)
                || "/topic/echo".equals(destination)) return;
        var matcher = ROOM_TOPIC.matcher(destination == null ? "" : destination);
        if (!matcher.matches()) throw new StompAuthException(ErrorCode.ACCESS_DENIED);
        final long roomId;
        try { roomId = Long.parseLong(matcher.group(1)); }
        catch (NumberFormatException e) { throw new StompAuthException(ErrorCode.INVALID_INPUT); }
        if (!rooms.existsById(roomId)) throw new StompAuthException(ErrorCode.ROOM_NOT_FOUND);
        if (!members.existsByRoomIdAndUserId(roomId, user.userId()))
            throw new StompAuthException(ErrorCode.NOT_ROOM_MEMBER);
    }
}
