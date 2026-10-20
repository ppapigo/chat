package com.example.chat.global.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageCommittedListener {

    private final ChatMessagePublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(ChatMessageSavedEvent event) {
        try {
            publisher.publisheMessage(event.message());
        } catch (RuntimeException ex) {
            // The DB commit is already complete. Do not report a false send failure
            // to the STOMP handler or attempt to roll back a committed message.
            // Delivery retries require a durable outbox, not an in-memory event.
            log.error("Chat message committed but STOMP broadcast failed: messageId={}, roomId={}",
                    event.message().id(), event.message().roomId(), ex);
        }
    }
}
