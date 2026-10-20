package com.example.chat.global.message;

import com.example.chat.auth.ChatUserPrincipal;
import com.example.chat.global.message.dto.MessageResponse;
import com.example.chat.global.room.ChatRoom;
import com.example.chat.global.room.ChatRoomRepository;
import com.example.chat.global.room.RoomMember;
import com.example.chat.global.room.RoomMemberRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Real MySQL transaction integration test, enabled only when an isolated
 * database is configured and RUN_CHAT_INTEGRATION_TESTS=true.
 *
 * The publisher is mocked: this verifies when a STOMP broadcast is requested,
 * not network delivery to a real WebSocket client.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_CHAT_INTEGRATION_TESTS", matches = "true")
class ChatMessageMySqlTransactionIT {

    private static final long SENDER_ID = 90102026L;

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ChatRoomRepository rooms;
    @Autowired
    private RoomMemberRepository members;
    @Autowired
    private ChatMessageRepository messages;
    @Autowired
    private ChatMessageService service;

    @MockitoBean
    private ChatMessagePublisher publisher;

    private TransactionTemplate transactions;
    private Long roomId;

    @BeforeEach
    void createFixture() {
        transactions = new TransactionTemplate(transactionManager);
        roomId = transactions.execute(status -> {
            ChatRoom room = rooms.saveAndFlush(new ChatRoom(
                    "tx-test-" + UUID.randomUUID(), "integration test", SENDER_ID, "test@example.com"));
            members.saveAndFlush(new RoomMember(room, SENDER_ID, "test@example.com"));
            return room.getId();
        });
        assertNotNull(roomId);
        clearInvocations(publisher);
    }

    @AfterEach
    void removeFixture() {
        if (roomId == null) return;
        transactions.executeWithoutResult(status -> {
            messages.deleteByRoomId(roomId);
            messages.flush();
            members.deleteByRoomId(roomId);
            members.flush();
            rooms.deleteById(roomId);
        });
    }

    private ChatUserPrincipal sender() {
        return new ChatUserPrincipal(SENDER_ID, "test@example.com", "tester",
                "integration-test", Instant.now().plusSeconds(60));
    }

    @Test
    void committedMessageIsPersistedAndPublishedOnlyAfterCommit() {
        String body = "committed-" + UUID.randomUUID();

        Long messageId = transactions.execute(status -> {
            MessageResponse response = service.send(roomId, sender(), body);
            assertNotNull(response.id());
            messages.flush(); // force the insert while the transaction is still active
            verifyNoInteractions(publisher);
            return response.id();
        });

        assertNotNull(messageId);
        assertTrue(messages.existsById(messageId));
        verify(publisher, times(1)).publisheMessage(argThat(message ->
                message.id().equals(messageId)
                && message.roomId().equals(roomId)
                && message.content().equals(body)));
    }

    @Test
    void rolledBackMessageIsNeitherPersistedNorPublished() {
        String body = "rolled-back-" + UUID.randomUUID();

        Long messageId = transactions.execute(status -> {
            MessageResponse response = service.send(roomId, sender(), body);
            assertNotNull(response.id());
            messages.flush(); // the SQL INSERT can happen before commit
            verifyNoInteractions(publisher);
            status.setRollbackOnly();
            return response.id();
        });

        assertNotNull(messageId);
        assertFalse(messages.existsById(messageId));
        verifyNoInteractions(publisher);
    }

    @Test
    void postCommitStompFailureDoesNotRollbackSavedMessage() {
        String body = "publisher-failure-" + UUID.randomUUID();
        doThrow(new IllegalStateException("simulated STOMP failure"))
                .when(publisher).publisheMessage(any(MessageResponse.class));

        Long messageId = assertDoesNotThrow(() -> transactions.execute(status -> {
            MessageResponse response = service.send(roomId, sender(), body);
            return response.id();
        }));

        assertNotNull(messageId);
        assertTrue(messages.existsById(messageId));
        verify(publisher, times(1)).publisheMessage(argThat(message ->
                message.id().equals(messageId) && message.roomId().equals(roomId)));
    }
}
