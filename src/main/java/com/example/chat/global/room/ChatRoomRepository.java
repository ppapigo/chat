package com.example.chat.global.room;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    boolean existsByName(String name);

    Page<ChatRoom> findAllByOrderByCreatedAtDesc(Pageable pageable);

}
