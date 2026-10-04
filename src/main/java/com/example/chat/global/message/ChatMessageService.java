package com.example.chat.global.message;

import com.example.chat.auth.ChatUserPrincipal;
import com.example.chat.global.exception.BusinessException;
import com.example.chat.global.exception.ErrorCode;
import com.example.chat.global.exception.ForbiddenException;
import com.example.chat.global.exception.NotFoundException;
import com.example.chat.global.message.dto.MessagePageResponse;
import com.example.chat.global.message.dto.MessageResponse;
import com.example.chat.global.room.ChatRoom;
import com.example.chat.global.room.ChatRoomRepository;
import com.example.chat.global.room.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageService {
    public static final int MAX_CONTEXT_LENGTH = 1000;
    public static final int MAX_PAGE_SIZE = 100;

    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessagePublisher publisher;

    private ChatRoom findRoom(Long roomId){
        return chatRoomRepository.findById(roomId)
                .orElseThrow(()->new NotFoundException(ErrorCode.ROOM_NOT_FOUND));
    }

    private void requireMember(Long roomId, Long userId){
        if (!roomMemberRepository.existsByRoomIdAndUserId(roomId,userId)){
            throw new ForbiddenException(ErrorCode.NOT_ROOM_MEMBER);
        }
    }

    private MessageResponse saveAndPublish(ChatMessage message){
        MessageResponse response = MessageResponse.from(chatMessageRepository.save(message));
        publisher.publisheMessage(response);
        return response;
    }

    @Transactional
    public MessageResponse send(Long roomId, ChatUserPrincipal sender, String content){
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (text.length()>MAX_CONTEXT_LENGTH){
            throw new BusinessException(ErrorCode.MESSAGE_TOO_LONG);
        }
        ChatRoom room = findRoom(roomId);
        requireMember(roomId, sender.userId());

        return saveAndPublish(
                new ChatMessage(room, MessageType.TALK, sender.userId(), sender.getName(), text)
        );
    }

    @Transactional
    public MessageResponse system(Long roomId, MessageType type, ChatUserPrincipal principal){
        ChatRoom room = findRoom(roomId);
        String text = principal.getName() + ((type == MessageType.ENTER) ? "님이 입장했습니다." :
                "님이 퇴장했습니다.");
        return saveAndPublish(
                new ChatMessage(room,type, principal.userId(), principal.getName(), text)
        );

    }

    @Transactional(readOnly = true)
    public MessagePageResponse history(Long roomId, ChatUserPrincipal principal, Long before, int size){
        findRoom(roomId);
        requireMember(roomId, principal.userId());

        int limit = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Pageable page = PageRequest.of(0, limit+1);

        List<ChatMessage> rows = before == null
                ? chatMessageRepository.findByRoomIdOrderByIdDesc(roomId, page)
                : chatMessageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, before, page);

        boolean hasMore = rows.size() > limit;
        List<ChatMessage> window = hasMore ? rows.subList(0,limit) : rows;
        List<MessageResponse> ascending =
                new ArrayList<>(window.stream().map(MessageResponse::from).toList());

        Collections.reverse(ascending);
        Long nextBefore = ascending.isEmpty() ? null : ascending.get(0).id();
        return new MessagePageResponse(ascending, hasMore, nextBefore);
    }
}
