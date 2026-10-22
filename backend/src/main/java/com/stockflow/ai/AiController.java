package com.stockflow.ai;

import com.stockflow.store.StoreContextProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/ai")
class AiController {

    private final StoreContextProvider contexts;
    private final AiAnalyticsService analytics;
    private final AiConversationService conversations;

    AiController(
            StoreContextProvider contexts,
            AiAnalyticsService analytics,
            AiConversationService conversations) {
        this.contexts = contexts;
        this.analytics = analytics;
        this.conversations = conversations;
    }

    @PostMapping("/query")
    AiModels.AiResponse query(@Valid @RequestBody AiModels.QueryRequest request) {
        return analytics.query(contexts.current(), request);
    }

    @GetMapping("/conversations")
    AiModels.ConversationList conversations(
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return conversations.list(contexts.current(), limit);
    }

    @GetMapping("/conversations/{conversationId}")
    AiModels.ConversationDetail conversation(@PathVariable UUID conversationId) {
        return conversations.get(contexts.current(), conversationId);
    }
}
