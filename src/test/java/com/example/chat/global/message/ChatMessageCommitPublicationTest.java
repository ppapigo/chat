package com.example.chat.global.message;

import com.example.chat.auth.ChatUserPrincipal;
import com.example.chat.global.message.dto.MessageResponse;
import com.example.chat.global.room.ChatRoom;
import com.example.chat.global.room.ChatRoomRepository;
import com.example.chat.global.room.RoomMemberRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests the AFTER_COMMIT event lifecycle with mocked persistence.
 * This exercises Spring transaction synchronization, not a real MySQL commit.
 */
class ChatMessageCommitPublicationTest {
    private AnnotationConfigApplicationContext context;
    private ChatMessagePublisher publisher;
    private ChatMessageService service;

    @BeforeEach
    void setUp() {
        publisher = mock(ChatMessagePublisher.class);
        context = new AnnotationConfigApplicationContext();
        context.registerBean(ChatMessageCommittedListener.class,
                () -> new ChatMessageCommittedListener(publisher));
        context.refresh();

        ChatRoomRepository rooms = mock(ChatRoomRepository.class);
        RoomMemberRepository members = mock(RoomMemberRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        ChatRoom room = mock(ChatRoom.class);
        when(room.getId()).thenReturn(7L);
        when(rooms.findById(7L)).thenReturn(java.util.Optional.of(room));
        when(members.existsByRoomIdAndUserId(7L, 10L)).thenReturn(true);
        when(messages.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new ChatMessageService(rooms, members, messages, context);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        if (context != null) context.close();
    }

    private void beginSimulatedTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void complete(int status) {
        TransactionSynchronizationUtils.triggerAfterCompletion(status);
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private void sendMessage() {
        service.send(7L, new ChatUserPrincipal(10L, "test@example.com", "tester",
                "token", Instant.now().plusSeconds(60)), "hello");
    }

    @Test
    void broadcastOnlyAfterCommit() {
        beginSimulatedTransaction();
        sendMessage();
        verifyNoInteractions(publisher);

        complete(TransactionSynchronization.STATUS_COMMITTED);
        verify(publisher, times(1)).publisheMessage(any(MessageResponse.class));
    }

    @Test
    void rollbackMustNotBroadcast() {
        beginSimulatedTransaction();
        sendMessage();

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verifyNoInteractions(publisher);
    }

    @Test
    void broadcastFailureDoesNotFailCommittedTransaction() {
        doThrow(new IllegalStateException("STOMP unavailable"))
                .when(publisher).publisheMessage(any(MessageResponse.class));
        beginSimulatedTransaction();
        sendMessage();

        assertDoesNotThrow(() -> complete(TransactionSynchronization.STATUS_COMMITTED));
        verify(publisher).publisheMessage(any(MessageResponse.class));
    }
}
