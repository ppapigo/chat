package com.example.chat.presence;

import com.example.chat.auth.ChatUserPrincipal;
import com.example.chat.global.message.ChatMessageService;
import com.example.chat.global.message.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class RoomPresenceListener {
    private final RoomPresenceTracker tracker;
    private final ChatMessageService chatMessageService;

    private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/room/(\\d+)$");

    private static Optional<Long> roomIdOf(String destination){
        if (destination == null){
            return Optional.empty();
        }
        Matcher m = ROOM_TOPIC.matcher(destination);
        return m.matches() ? Optional.of(Long.valueOf(m.group(1))) : Optional.empty();
    }

    private void leave(RoomPresenceTracker.Presence presence, Principal principal){
        ChatUserPrincipal.from(principal)
                .ifPresent(user -> chatMessageService.system(presence.roomId(),MessageType.LEAVE, user));
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event){
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Optional<Long> roomId = roomIdOf(accessor.getDestination());
        Optional<ChatUserPrincipal> user = ChatUserPrincipal.from(event.getUser());
        if (roomId.isEmpty() || user.isEmpty()){
            return;
        }

        boolean first = tracker.subscribe(
                accessor.getSessionId(),
                accessor.getSubscriptionId(),
                roomId.get(),
                user.get().email()
        );

        if (first) {
            chatMessageService.system(roomId.get(), MessageType.ENTER,user.get());
        }
    }
}
