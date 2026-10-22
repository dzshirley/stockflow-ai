package com.stockflow.ai;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {
    Optional<AiConversation> findByIdAndStoreIdAndUserId(UUID id, UUID storeId, UUID userId);

    List<AiConversation> findByStoreIdAndUserIdAndStatusOrderByUpdatedAtDesc(
            UUID storeId, UUID userId, AiConversation.Status status, Pageable pageable);
}
