package com.example.chat.global.room;

import com.example.chat.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_rooms")
@Getter
@NoArgsConstructor
public class ChatRoom extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(name ="owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "owner_username", nullable = false, length = 50)
    private String ownerUsername;

    public ChatRoom(String name, String description, Long ownerUserId, String ownerUsername){
        this.name=name;
        this.description=description;
        this.ownerUserId=ownerUserId;
        this.ownerUsername=ownerUsername;
    }

    public boolean isOwnedBy(Long userId){
        return ownerUserId.equals(userId);
    }
}
