package com.example.chat.global.message;

import com.example.chat.global.entity.BaseTimeEntity;
import com.example.chat.global.room.ChatRoom;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_messages", indexes = @Index(
        name = "idx_chat_messages_room_id_id", columnList = "room_id, id"))
@Getter
@NoArgsConstructor
public class ChatMessage extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @Column(name = "sender_user_id")
    private Long senderUserId;

    @Column(name = "sender_username", nullable = false, length = 50)
    private String senderUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MessageType type;

    @Column(nullable = false, length = 1000)
    private String content;

    public ChatMessage(ChatRoom room, MessageType type, Long senderUserId,
                       String senderUsername, String content){
        this.room=room;
        this.type=type;
        this.senderUserId=senderUserId;
        this.senderUsername=senderUsername;
        this.content=content;
    }
}
