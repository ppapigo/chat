package com.example.chat.global.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    long countByRoomId(Long roomId);

    List<RoomMember> findAllByRoomIdOrderByJoinedAtAsc(Long roomId);

    void deleteByRoomIdAndUserId(Long roomId, Long userId);

    void deleteByRoomId(Long roomId);
}
