package com.stockflow.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {
    List<AiMessage> findByConversationIdOrderByCreatedAtAscIdAsc(UUID conversationId);
}
