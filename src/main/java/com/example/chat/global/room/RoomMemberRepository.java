package com.example.chat.global.room;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
    boolean existByRoomIdAndUserId(Long roomId, Long userId);

    long countByRoomId(Long roomId);

    List<RoomMember> findAllByRoomIdOrderByJoinedAtAsc(Long roomId);

    void deletedByRoomIdAndUserId(Long roomId, Long userId);

    void deletedByRoomId(Long roomId);
}
