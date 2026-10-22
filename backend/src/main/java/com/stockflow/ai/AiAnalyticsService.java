package com.stockflow.ai;

import com.stockflow.ai.AiModels.AiResponse;
import com.stockflow.store.StoreContext;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AiAnalyticsService {

    private final AiConversationService conversations;
    private final AiChatOrchestrator orchestrator;

    AiAnalyticsService(AiConversationService conversations, AiChatOrchestrator orchestrator) {
        this.conversations = conversations;
        this.orchestrator = orchestrator;
    }

    public AiResponse query(StoreContext context, AiModels.QueryRequest request) {
        var turn = conversations.begin(context, request.conversationId(), request.question());
        var invocation = new AiModels.InvocationContext(
                UUID.randomUUID(), turn.conversationId(), turn.userMessageId(), context);
        var response = orchestrator.execute(
                invocation, turn.question(), turn.history(), UUID.randomUUID());
        conversations.finish(context, response);
        return response;
    }
}
