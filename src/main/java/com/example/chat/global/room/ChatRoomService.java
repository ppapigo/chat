package com.example.chat.global.room;

import com.example.chat.auth.ChatUserPrincipal;
import com.example.chat.global.exception.DuplicateException;
import com.example.chat.global.exception.ErrorCode;
import com.example.chat.global.exception.ForbiddenException;
import com.example.chat.global.exception.NotFoundException;
import com.example.chat.global.message.ChatMessagePublisher;
import com.example.chat.global.message.ChatMessageRepository;
import com.example.chat.global.message.dto.RoomEvent;
import com.example.chat.global.message.dto.RoomEventType;
import com.example.chat.global.room.dto.MemberResponse;
import com.example.chat.global.room.dto.RoomCreateRequest;
import com.example.chat.global.room.dto.RoomResponse;
import com.example.chat.presence.RoomPresenceTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomService {
    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RoomPresenceTracker presenceTracker;
    private final ChatMessagePublisher publisher;

    private RoomResponse toResponse(ChatRoom room){
        return RoomResponse.of(
                room,
                roomMemberRepository.countByRoomId(room.getId()),
                presenceTracker.onlineCount(room.getId()));
    }

    private ChatRoom findRoom(Long roomId){
        return chatRoomRepository.findById(roomId)
                .orElseThrow(()->new NotFoundException(ErrorCode.ROOM_NOT_FOUND));
    }

    private void requireMember(Long roomId, Long userId){
        if (!roomMemberRepository.existsByRoomIdAndUserId(roomId,userId)){
            throw new ForbiddenException(ErrorCode.NOT_ROOM_MEMBER);
        }
    }

    @Transactional
    public RoomResponse create(RoomCreateRequest request, ChatUserPrincipal principal){
        if (chatRoomRepository.existsByName(request.name())){
            throw new DuplicateException(ErrorCode.DUPLICATE_ROOM_NAME);
        }

        ChatRoom room = chatRoomRepository.save(
                new ChatRoom(request.name(),
                        request.description(),
                        principal.userId(),
                        principal.email())
        );
        roomMemberRepository.save(
                new RoomMember(room, principal.userId(), principal.email())
        );
        RoomResponse response = toResponse(room);
        publisher.publishRoomEvent(new RoomEvent(RoomEventType.ROOM_CREATED,response));

        return response;
    }

    @Transactional(readOnly = true)
    public Page<RoomResponse> list(Pageable pageable){
        return chatRoomRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toResponse);
    }
     @Transactional(readOnly = true)
    public RoomResponse get(Long roomId){
        return toResponse(findRoom(roomId));
     }

     @Transactional(readOnly = true)
    public RoomResponse join(Long roomId, ChatUserPrincipal principal){
        ChatRoom room =findRoom(roomId);
        if(!roomMemberRepository.existsByRoomIdAndUserId(roomId, principal.userId())){
            roomMemberRepository.save(new RoomMember(room, principal.userId(), principal.email()));
            publisher.publishRoomEvent(new RoomEvent(RoomEventType.MEMBER_COUNT, toResponse(room)));
        }

        return toResponse(room);
     }

     @Transactional
    public void leave(Long roomId, ChatUserPrincipal principal){
        ChatRoom room = findRoom(roomId);
        if(roomMemberRepository.existsByRoomIdAndUserId(roomId, principal.userId())){
            roomMemberRepository.deleteByRoomIdAndUserId(roomId, principal.userId());
            publisher.publishRoomEvent(new RoomEvent(RoomEventType.MEMBER_COUNT, toResponse(room)));
        }
     }

     @Transactional
    public void delete(Long roomId){
        ChatRoom room = findRoom(roomId);
        RoomResponse tmp = toResponse(room);
        chatMessageRepository.deleteByRoomId(roomId);
        roomMemberRepository.deleteByRoomId(roomId);
        chatRoomRepository.delete(room);
        publisher.publishRoomEvent(new RoomEvent(RoomEventType.ROOM_DELETED,tmp));
     }


     @Transactional(readOnly = true)
    public List<MemberResponse> members(Long roomId, ChatUserPrincipal principal){
        findRoom(roomId);
        requireMember(roomId, principal.userId());

        return roomMemberRepository.findAllByRoomIdOrderByJoinedAtAsc(roomId).stream()
                .map(member -> MemberResponse.of(member, presenceTracker.isOnline(roomId, member.getUsername())))
                .toList();
     }


}
