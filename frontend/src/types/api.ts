export type Role = 'ADMIN' | 'STAFF'
export type ProductStatus = 'ACTIVE' | 'ARCHIVED'
export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'PAYMENT_FAILED' | 'CANCELLED' | 'EXPIRED'

export interface AuthUser {
  userId: string
  storeId: string
  email: string
  displayName: string
  role: Role
}

export interface LoginInput { email: string; password: string; storeId?: string }
export interface RegisterInput {
  email: string
  password: string
  displayName: string
  storeName: string
  currencyCode: string
  timeZone: string
}

export interface Store {
  id: string
  name: string
  currencyCode: string
  timeZone: string
  role: Role
}

export interface StoreMember {
  userId: string
  email: string
  displayName: string
  role: Role
}

export interface Product {
  id: string
  sku: string
  name: string
  category: string
  price: number
  unitCost: number
  status: ProductStatus
  createdAt: string
  updatedAt: string
}

export interface ProductInput {
  sku: string
  name: string
  category: string
  price: number
  unitCost: number
  initialQuantity: number
  reorderPoint: number
}

export type ProductUpdate = Partial<Omit<ProductInput, 'initialQuantity' | 'reorderPoint'>>

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface InventoryBalance {
  productId: string
  sku: string
  productName: string
  availableQuantity: number
  reservedQuantity: number
  reorderPoint: number
  lowStock: boolean
  version: number
  updatedAt: string
}

export interface InventoryAdjustment {
  id: string
  productId: string
  availableDelta: number
  reservedDelta: number
  reason: string
  actorUserId: string
  createdAt: string
}

export interface OrderLine {
  productId: string
  sku: string
  name: string
  quantity: number
  unitPrice: number
  discountAmount: number
}

export interface Order {
  id: string
  status: OrderStatus
  subtotal: number
  discountTotal: number
  total: number
  currencyCode: string
  expiresAt: string
  createdAt: string
  updatedAt: string
  items: OrderLine[]
}

export type AnalyticsPreset = 'LAST_7_DAYS' | 'LAST_30_DAYS' | 'LAST_90_DAYS' | 'THIS_MONTH' | 'LAST_MONTH' | 'CUSTOM'
export type TrendInterval = 'DAY' | 'WEEK' | 'MONTH'
export type InventoryRiskLevel = 'OUT_OF_STOCK' | 'LOW_STOCK' | 'AT_RISK' | 'HEALTHY' | 'NO_SALES'

export interface AnalyticsPeriod {
  startDate: string
  endDateExclusive: string
  timeZone: string
}

export interface AnalyticsFilters {
  preset: AnalyticsPreset
  startDate?: string
  endDateExclusive?: string
  comparePreviousPeriod?: boolean
}

export interface AnalyticsOverview {
  period: AnalyticsPeriod
  metrics: {
    revenue: number
    currency: string
    orders: number
    averageOrderValue: number | null
    unitsSold: number
  }
  comparison: null | {
    period: AnalyticsPeriod
    revenue: number
    revenueChange: number
    revenueGrowthPercent: number | null
    comparable: boolean
  }
}

export interface RevenueTrend {
  period: AnalyticsPeriod
  interval: TrendInterval
  currency: string
  points: Array<{ bucketStart: string; revenue: number; orders: number; unitsSold: number }>
}

export interface ProductPerformance {
  productId: string
  sku: string
  name: string
  revenue: number
  unitsSold: number
  orders: number
}

export interface ProductPerformanceResponse {
  period: AnalyticsPeriod
  currency: string
  items: ProductPerformance[]
}

export interface CategoryPerformance {
  category: string
  revenue: number
  unitsSold: number
  orders: number
}

export interface CategoryPerformanceResponse {
  period: AnalyticsPeriod
  currency: string
  items: CategoryPerformance[]
}

export interface InventoryAnalyticsSummary {
  activeProducts: number
  availableUnits: number
  reservedUnits: number
  lowStockProducts: number
  unitsSoldInPeriod: number
  averageDailyUnitsSold: number
  salesPeriod: AnalyticsPeriod
}

export interface InventoryRiskItem {
  productId: string
  sku: string
  name: string
  category: string
  availableQuantity: number
  reservedQuantity: number
  reorderPoint: number
  unitsSold: number
  averageDailyUnitsSold: number
  daysOfCover: number | null
  riskLevel: InventoryRiskLevel
}

export interface InventoryRiskResponse {
  salesPeriod: AnalyticsPeriod
  items: InventoryRiskItem[]
}

export type AiResponseType = 'ANALYTICS' | 'CLARIFICATION' | 'UNSUPPORTED'
export type AiChartType = 'LINE' | 'BAR'

export interface AiMetric {
  key: string
  label: string
  value: number
  unit: string
  comparisonValue: number | null
  changePercent: number | null
  comparable: boolean
}

export interface AiChart {
  id: string
  type: AiChartType
  title: string
  xKey: string
  series: Array<{ key: string; label: string; unit: string }>
  data: Array<Record<string, string | number | null>>
}

export interface AiDataTable {
  id: string
  title: string
  columns: string[]
  rows: Array<Record<string, string | number | null>>
}

export interface AiResponse {
  conversationId: string
  messageId: string
  responseType: AiResponseType
  summary: string
  metrics: AiMetric[]
  charts: AiChart[]
  tables: AiDataTable[]
  evidence: Array<{ toolExecutionId: string; toolName: string; description: string; period: AnalyticsPeriod }>
  assumptions: string[]
  limitations: string[]
  appliedFilters: Array<{ name: string; value: string }>
  provider: string
}

export interface AiConversationSummary {
  id: string
  title: string
  createdAt: string
  updatedAt: string
}

export interface AiConversationMessage {
  id: string
  role: 'USER' | 'ASSISTANT'
  content: string
  createdAt: string
  response: AiResponse | null
}

export interface AiConversationDetail extends AiConversationSummary {
  messages: AiConversationMessage[]
}

export type BusinessAnalysisType = 'AUTO' | 'REVENUE_INVESTIGATION' | 'INVENTORY_RISK' | 'SCENARIO_ANALYSIS'
export type BusinessReportStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
export type BusinessPriority = 'HIGH' | 'MEDIUM' | 'LOW'
export type BusinessScenarioType = 'UNIT_SALES_CHANGE' | 'PRICE_CHANGE' | 'DISCOUNT_CHANGE' | 'SALES_VELOCITY_CHANGE'

export interface BusinessReportSummary {
  id: string
  reportType: Exclude<BusinessAnalysisType, 'AUTO'>
  title: string
  status: BusinessReportStatus
  periodStart: string
  periodEndExclusive: string
  dataAsOf: string | null
  createdAt: string
  completedAt: string | null
  failureCode: string | null
}

export interface BusinessMetric {
  key: string
  label: string
  value: number
  comparisonValue: number | null
  absoluteChange: number | null
  changePercent: number | null
  unit: string
  evidenceIds: string[]
}

export interface BusinessEvidence {
  id: string
  source: 'ANALYTICS_TOOL' | 'ORDER_SNAPSHOT' | 'INVENTORY_SNAPSHOT'
  toolExecutionId: string | null
  metricKey: string
  value: number
  unit: string
  periodStart: string
  periodEndExclusive: string
  timeZone: string
  filters: Record<string, string>
  observedAt: string
}

export interface BusinessFinding {
  id: string
  kind: 'VERIFIED_OBSERVATION' | 'DETERMINISTIC_CALCULATION'
  title: string
  statement: string
  evidenceIds: string[]
  calculationDefinition: string | null
}

export interface BusinessHypothesis {
  id: string
  statement: string
  rationale: string
  supportingEvidenceIds: string[]
  counterEvidenceIds: string[]
  status: 'UNTESTED' | 'SUPPORTED' | 'REFUTED' | 'INCONCLUSIVE'
  validationPlan: string
}

export interface BusinessRecommendation {
  id: string
  title: string
  description: string
  rationale: string
  findingIds: string[]
  hypothesisIds: string[]
  evidenceIds: string[]
  priority: BusinessPriority
  expectedImpact: number | null
  expectedImpactUnit: string | null
  assumptions: string[]
  risks: string[]
  nextSteps: string[]
  approvalRequired: boolean
}

export interface BusinessScenario {
  id: string
  type: BusinessScenarioType
  changePercent: number
  statement: string
  baselineValue: number | null
  modeledValue: number | null
  unit: string
  baselineGrossProfit: number | null
  modeledGrossProfit: number | null
  grossProfitChange: number | null
  evidenceIds: string[]
  assumptions: string[]
  limitations: string[]
}

export interface BusinessChart {
  id: string
  type: 'BAR' | 'WATERFALL'
  title: string
  categoryLabel: string
  valueLabel: string
  unit: string
  points: Array<{ key: string; label: string; value: number }>
}

export interface BusinessAnalysisReport {
  reportId: string
  schemaVersion: number
  reportVersion: number
  reportType: Exclude<BusinessAnalysisType, 'AUTO'>
  title: string
  summary: string
  storeId: string
  createdByUserId: string
  status: BusinessReportStatus
  periodStart: string
  periodEndExclusive: string
  comparisonPeriodStart: string
  comparisonPeriodEndExclusive: string
  timeZone: string
  currency: string
  dataAsOf: string
  metrics: BusinessMetric[]
  evidence: BusinessEvidence[]
  findings: BusinessFinding[]
  hypotheses: BusinessHypothesis[]
  recommendations: BusinessRecommendation[]
  scenarios: BusinessScenario[]
  charts: BusinessChart[]
  limitations: Array<{ code: string; description: string; affectedFindingIds: string[] }>
  modelMetadata: {
    provider: string
    model: string
    promptVersion: string
    inputTokens: number | null
    outputTokens: number | null
    totalTokens: number | null
    pricingVersion: string | null
    estimatedCostUsd: number | null
    latencyMs: number
  }
  createdAt: string
  completedAt: string
}

export interface BusinessReportDetail {
  metadata: BusinessReportSummary
  report: BusinessAnalysisReport | null
}

export interface BusinessReportCreateRequest {
  question: string
  analysisType?: BusinessAnalysisType
  preset: AnalyticsPreset
  startDate?: string
  endDateExclusive?: string
  scenarios?: Array<{ type: BusinessScenarioType; changePercent: number }>
}

export type RestockProposalStatus =
  | 'PROPOSED' | 'APPROVED' | 'EXECUTING' | 'EXECUTED'
  | 'REJECTED' | 'CANCELLED' | 'STALE' | 'FAILED'

export interface RestockProposal {
  id: string
  productId: string
  sku: string
  productName: string
  status: RestockProposalStatus
  proposedQuantity: number
  rationale: string
  assumptions: string[]
  sourceReportId: string
  sourceReportVersion: number
  recommendationId: string
  evidenceId: string
  snapshotAvailable: number
  snapshotReserved: number
  snapshotReorderPoint: number
  snapshotInventoryVersion: number
  snapshotInventoryUpdatedAt: string
  sourceDataAsOf: string
  createdByUserId: string
  approvedByUserId: string | null
  approvedAt: string | null
  executionCommandId: string | null
  inventoryAdjustmentId: string | null
  executedAt: string | null
  failureCode: string | null
  staleReason: string | null
  modelProvider: string | null
  modelName: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface RestockProposalHistory {
  events: Array<{
    id: string
    fromStatus: RestockProposalStatus | null
    toStatus: RestockProposalStatus
    action: string
    actorUserId: string | null
    reasonCode: string | null
    createdAt: string
  }>
  attempts: Array<{
    id: string
    commandId: string
    status: 'STARTED' | 'SUCCEEDED' | 'FAILED' | 'ABANDONED'
    attemptNumber: number
    inventoryAdjustmentId: string | null
    errorCode: string | null
    startedAt: string
    completedAt: string | null
  }>
}

export interface ProblemViolation { field: string; message: string }
export interface ProblemDetails {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  code?: string
  violations?: ProblemViolation[]
}
