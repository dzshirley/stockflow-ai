import type { AiConversationDetail, AiConversationSummary, AiResponse } from '../types/api'
import { apiRequest } from './api'

export function queryAi(question: string, conversationId?: string): Promise<AiResponse> {
  return apiRequest('/api/ai/query', {
    method: 'POST',
    csrf: true,
    body: JSON.stringify({ question, conversationId: conversationId || null }),
  })
}

export async function listAiConversations(limit = 20): Promise<AiConversationSummary[]> {
  const response = await apiRequest<{ items: AiConversationSummary[] }>(`/api/ai/conversations?limit=${limit}`)
  return response.items
}

export function getAiConversation(id: string): Promise<AiConversationDetail> {
  return apiRequest(`/api/ai/conversations/${id}`)
}
