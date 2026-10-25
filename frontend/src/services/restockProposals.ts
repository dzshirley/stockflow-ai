import { apiRequest } from './api'
import type { RestockProposal, RestockProposalHistory } from '../types/api'
import { newIdempotencyKey, newUuid } from '../utils/format'

export function listRestockProposals(limit = 50) {
  return apiRequest<{ items: RestockProposal[] }>(`/api/restock-proposals?limit=${limit}`).then((value) => value.items)
}

export function getRestockProposal(id: string) {
  return apiRequest<RestockProposal>(`/api/restock-proposals/${id}`)
}

export function getRestockProposalHistory(id: string) {
  return apiRequest<RestockProposalHistory>(`/api/restock-proposals/${id}/history`)
}

export function generateRestockProposals(input: {
  question: string
  sourceReportId: string
  recommendationId: string
}, idempotencyKey: string = newIdempotencyKey()) {
  return apiRequest<{ items: RestockProposal[] }>('/api/restock-proposals/generate', {
    method: 'POST', csrf: true, headers: { 'Idempotency-Key': idempotencyKey }, body: JSON.stringify(input),
  }).then((value) => value.items)
}

function transition(id: string, action: 'approve' | 'reject' | 'cancel', expectedVersion: number, reason?: string) {
  return apiRequest<RestockProposal>(`/api/restock-proposals/${id}/${action}`, {
    method: 'POST', csrf: true, body: JSON.stringify({ expectedVersion, reason }),
  })
}

export const approveRestockProposal = (id: string, version: number) => transition(id, 'approve', version)
export const rejectRestockProposal = (id: string, version: number, reason: string) => transition(id, 'reject', version, reason)
export const cancelRestockProposal = (id: string, version: number, reason: string) => transition(id, 'cancel', version, reason)

export function executeRestockProposal(id: string, version: number, commandId: string = newUuid()) {
  return apiRequest<RestockProposal>(`/api/restock-proposals/${id}/execute`, {
    method: 'POST', csrf: true, headers: { 'Idempotency-Key': commandId },
    body: JSON.stringify({ expectedVersion: version, received: true }),
  })
}
