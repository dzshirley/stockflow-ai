package com.stockflow.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.ai.AiConfiguration.AiProperties;
import com.stockflow.common.error.ApiExceptions;
import com.stockflow.store.StoreContext;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiConversationService {

    private static final int MAX_RETURNED_MESSAGES = 200;
    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;
    private final Clock clock;

    AiConversationService(
            AiConversationRepository conversations,
            AiMessageRepository messages,
            ObjectMapper objectMapper,
            AiProperties properties,
            Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    BeginTurn begin(StoreContext context, UUID conversationId, String rawQuestion) {
        var question = normalizeQuestion(rawQuestion);
        var now = Instant.now(clock);
        var conversation = conversationId == null
                ? new AiConversation(UUID.randomUUID(), context.storeId(), context.userId(), title(question), now)
                : owned(context, conversationId);
        if (conversationId == null) conversations.save(conversation);
        if (conversation.getStatus() != AiConversation.Status.ACTIVE) {
            throw ApiExceptions.conflict("AI_CONVERSATION_ARCHIVED", "The conversation is archived");
        }

        var existing = messages.findByConversationIdOrderByCreatedAtAscIdAsc(conversation.getId());
        var history = tail(existing, properties.maxHistoryMessages()).stream()
                .map(message -> new AiModels.HistoryMessage(message.getRole().name(), message.getContent()))
                .toList();
        var userMessage = new AiMessage(
                UUID.randomUUID(), conversation.getId(), context.storeId(), AiMessage.Role.USER,
                question, null, now);
        messages.save(userMessage);
        if (existing.isEmpty()) conversation.setTitle(title(question), now);
        else conversation.touch(now);
        return new BeginTurn(conversation.getId(), userMessage.getId(), question, history);
    }

    @Transactional
    void finish(StoreContext context, AiModels.AiResponse response) {
        var conversation = owned(context, response.conversationId());
        var payload = writeResponse(response);
        if (payload.length() > properties.maxResponseCharacters()) {
            throw ApiExceptions.badRequest(
                    "AI_RESPONSE_TOO_LARGE", "The validated AI response exceeds the configured size limit");
        }
        var now = Instant.now(clock);
        messages.save(new AiMessage(
                response.messageId(), response.conversationId(), context.storeId(), AiMessage.Role.ASSISTANT,
                response.summary(), payload, now));
        conversation.touch(now);
    }

    @Transactional(readOnly = true)
    public AiModels.ConversationList list(StoreContext context, int limit) {
        var items = conversations.findByStoreIdAndUserIdAndStatusOrderByUpdatedAtDesc(
                        context.storeId(), context.userId(), AiConversation.Status.ACTIVE, PageRequest.of(0, limit))
                .stream()
                .map(this::summary)
                .toList();
        return new AiModels.ConversationList(items);
    }

    @Transactional(readOnly = true)
    public AiModels.ConversationDetail get(StoreContext context, UUID conversationId) {
        var conversation = owned(context, conversationId);
        var all = messages.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId);
        var visible = tail(all, MAX_RETURNED_MESSAGES).stream().map(this::message).toList();
        return new AiModels.ConversationDetail(
                conversation.getId(), conversation.getTitle(), conversation.getCreatedAt(),
                conversation.getUpdatedAt(), visible);
    }

    private AiConversation owned(StoreContext context, UUID conversationId) {
        return conversations.findByIdAndStoreIdAndUserId(conversationId, context.storeId(), context.userId())
                .orElseThrow(() -> ApiExceptions.notFound("AI conversation"));
    }

    private String normalizeQuestion(String rawQuestion) {
        var question = rawQuestion == null ? "" : rawQuestion.strip().replaceAll("\\s+", " ");
        if (question.isBlank()) {
            throw ApiExceptions.badRequest("AI_QUESTION_REQUIRED", "Enter an analytics question");
        }
        if (question.length() > properties.maxQuestionLength()) {
            throw ApiExceptions.badRequest(
                    "AI_QUESTION_TOO_LONG",
                    "Questions may not exceed " + properties.maxQuestionLength() + " characters");
        }
        return question;
    }

    private String title(String question) {
        return question.length() <= 80 ? question : question.substring(0, 77) + "...";
    }

    private AiModels.ConversationSummary summary(AiConversation conversation) {
        return new AiModels.ConversationSummary(
                conversation.getId(), conversation.getTitle(), conversation.getCreatedAt(), conversation.getUpdatedAt());
    }

    private AiModels.ConversationMessage message(AiMessage message) {
        AiModels.AiResponse response = null;
        if (message.getResponsePayload() != null) {
            try {
                response = objectMapper.readValue(message.getResponsePayload(), AiModels.AiResponse.class);
            } catch (JsonProcessingException exception) {
                throw ApiExceptions.conflict(
                        "AI_RESPONSE_CORRUPT", "A persisted assistant response could not be read safely");
            }
        }
        return new AiModels.ConversationMessage(
                message.getId(), message.getRole().name(), message.getContent(), message.getCreatedAt(), response);
    }

    private String writeResponse(AiModels.AiResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize validated AI response", exception);
        }
    }

    private <T> List<T> tail(List<T> input, int limit) {
        if (input.size() <= limit) return List.copyOf(input);
        return new ArrayList<>(input.subList(input.size() - limit, input.size()));
    }

    record BeginTurn(
            UUID conversationId,
            UUID userMessageId,
            String question,
            List<AiModels.HistoryMessage> history) {
    }
}
