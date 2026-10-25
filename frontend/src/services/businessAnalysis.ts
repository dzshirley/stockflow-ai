import { apiRequest } from './api'
import type {
  BusinessAnalysisType,
  BusinessReportCreateRequest,
  BusinessReportDetail,
  BusinessReportStatus,
  BusinessReportSummary,
} from '../types/api'
import { newIdempotencyKey } from '../utils/format'

export function listBusinessReports(filters: {
  status?: BusinessReportStatus
  type?: Exclude<BusinessAnalysisType, 'AUTO'>
  limit?: number
} = {}) {
  const query = new URLSearchParams()
  if (filters.status) query.set('status', filters.status)
  if (filters.type) query.set('type', filters.type)
  query.set('limit', String(filters.limit ?? 20))
  return apiRequest<{ items: BusinessReportSummary[] }>(`/api/business-analysis/reports?${query}`).then((value) => value.items)
}

export function getBusinessReport(reportId: string) {
  return apiRequest<BusinessReportDetail>(`/api/business-analysis/reports/${reportId}`)
}

export function createBusinessReport(
  request: BusinessReportCreateRequest,
  idempotencyKey: string = newIdempotencyKey(),
) {
  return apiRequest<BusinessReportDetail>('/api/business-analysis/reports', {
    method: 'POST',
    csrf: true,
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(request),
  })
}
