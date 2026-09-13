package com.example.chat.global.room;

import com.example.chat.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

@Entity
@Table(name = "room_members", uniqueConstraints = @UniqueConstraint(
        name ="uk_room_member_room_user", columnNames = {"room_id", "user_id"}))
@Getter
@NoArgsConstructor
public class RoomMember extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String username;

    @CreatedDate
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    public RoomMember (ChatRoom room, Long userId, String username
    ) {
        this.room = room;
        this.userId = userId;
        this.username = username;
    }
}
