package com.example.chat.global.room.dto;

import com.example.chat.global.room.RoomMember;

import java.time.LocalDateTime;

public record MemberResponse(
        Long userId,
        String email,
        boolean online,
        LocalDateTime joinAt
) {
    public static MemberResponse of(RoomMember member, boolean online){
        return new MemberResponse(member.getId(), member.getUsername(), online, member.getJoinedAt());
    }
}
