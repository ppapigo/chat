package com.example.chat.presence;

import com.example.chat.auth.ChatUserPrincipal;
import com.example.chat.global.message.ChatMessageService;
import com.example.chat.global.message.MessageType;
import com.example.chat.global.message.ChatMessagePublisher;
import com.example.chat.global.message.dto.RoomEvent;
import com.example.chat.global.message.dto.RoomEventType;
import com.example.chat.global.room.ChatRoomService;
import com.example.chat.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class RoomPresenceListener {
    private final RoomPresenceTracker tracker;
    private final ChatMessageService chatMessageService;
    private final ChatRoomService chatRoomService;
    private final ChatMessagePublisher publisher;

    private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/room/(\\d+)$");

    private static Optional<Long> roomIdOf(String destination){
        if (destination == null){
            return Optional.empty();
        }
        Matcher m = ROOM_TOPIC.matcher(destination);
        return m.matches() ? Optional.of(Long.valueOf(m.group(1))) : Optional.empty();
    }

    private void leave(RoomPresenceTracker.Presence presence, Principal principal){
        if (!presence.lastForUser()) return;
        try {
            ChatUserPrincipal.from(principal)
                    .ifPresent(user -> chatMessageService.system(presence.roomId(),MessageType.LEAVE, user));
            publishCount(presence.roomId());
        } catch (NotFoundException ignored) {
            // A deleted room may still have active socket subscriptions.
        }
    }

    private void publishCount(Long roomId) {
        publisher.publishRoomEvent(new RoomEvent(RoomEventType.MEMBER_COUNT, chatRoomService.get(roomId)));
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
            publishCount(roomId.get());
        }
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        tracker.unsubscribe(accessor.getSessionId(), accessor.getSubscriptionId())
                .ifPresent(presence -> leave(presence, event.getUser()));
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        tracker.disconnect(event.getSessionId()).forEach(presence -> leave(presence, event.getUser()));
    }
}
