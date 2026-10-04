package com.example.chat.global.room;

import com.example.chat.auth.ChatUserPrincipal;
import com.example.chat.global.room.dto.MemberResponse;
import com.example.chat.global.room.dto.RoomCreateRequest;
import com.example.chat.global.room.dto.RoomResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomController {
    private final ChatRoomService chatRoomService;

    @GetMapping
    public PagedModel<RoomResponse> list(@PageableDefault(size = 20)Pageable pageable){
        return new PagedModel<>(chatRoomService.list(pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(
            @Valid @RequestBody RoomCreateRequest request,
            @AuthenticationPrincipal ChatUserPrincipal principal
            ){

        return chatRoomService.create(request,principal);

    }

    @GetMapping("/{id}")
    public RoomResponse get(@PathVariable Long id){
        return chatRoomService.get(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@roomSecurity.isOwner(#id, principal)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id){
        chatRoomService.delete(id);
    }

    @PostMapping("/{id}/join")
    public RoomResponse join(
            @PathVariable Long id,
            @AuthenticationPrincipal ChatUserPrincipal principal
    ){
        return chatRoomService.join(id,principal);
    }

    @DeleteMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(
            @PathVariable Long id,
            @AuthenticationPrincipal ChatUserPrincipal principal
    ){
        chatRoomService.leave(id,principal);
    }

    @GetMapping("/{id}/members")
    public List<MemberResponse> members(
            @PathVariable Long id,
            @AuthenticationPrincipal ChatUserPrincipal principal
    ){
        return chatRoomService.members(id, principal);
    }
}
