package com.example.chat.global.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    List<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long before, Pageable pageable);

    void deleteByRoomId(Long roomId);
}
