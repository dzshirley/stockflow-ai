import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { PageHeader } from '../components/AppShell'
import { ErrorPanel } from '../components/Feedback'
import { createBusinessReport, getBusinessReport, listBusinessReports } from '../services/businessAnalysis'
import type {
  AnalyticsPreset,
  BusinessAnalysisReport,
  BusinessAnalysisType,
  BusinessChart,
  BusinessMetric,
  BusinessReportCreateRequest,
} from '../types/api'
import { newIdempotencyKey } from '../utils/format'

const suggestions = [
  'Why did revenue change last month?',
  'Which products contributed most to the sales decline?',
  'Which high-selling products are currently at risk of running out?',
  'What if unit sales increase by 10%?',
]

export function BusinessInsightsPage() {
  const queryClient = useQueryClient()
  const [selectedId, setSelectedId] = useState<string>()
  const [question, setQuestion] = useState('')
  const [analysisType, setAnalysisType] = useState<BusinessAnalysisType>('AUTO')
  const [preset, setPreset] = useState<AnalyticsPreset>('LAST_30_DAYS')
  const [startDate, setStartDate] = useState('')
  const [endDateExclusive, setEndDateExclusive] = useState('')
  const [validationError, setValidationError] = useState('')
  const idempotencyKey = useRef(newIdempotencyKey())

  const history = useQuery({
    queryKey: ['business-analysis', 'reports'],
    queryFn: () => listBusinessReports(),
  })
  const detail = useQuery({
    queryKey: ['business-analysis', 'report', selectedId],
    queryFn: () => getBusinessReport(selectedId!),
    enabled: Boolean(selectedId),
  })
  const create = useMutation({
    mutationFn: (request: BusinessReportCreateRequest) => createBusinessReport(request, idempotencyKey.current),
    onSuccess: (result) => {
      idempotencyKey.current = newIdempotencyKey()
      setSelectedId(result.metadata.id)
      queryClient.setQueryData(['business-analysis', 'report', result.metadata.id], result)
      void queryClient.invalidateQueries({ queryKey: ['business-analysis', 'reports'] })
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: ['business-analysis', 'reports'] })
    },
  })

  function submit(event: React.FormEvent) {
    event.preventDefault()
    const normalized = question.trim()
    if (!normalized) return setValidationError('Enter a business question.')
    if (preset === 'CUSTOM' && (!startDate || !endDateExclusive || startDate >= endDateExclusive)) {
      return setValidationError('Choose a valid custom start and exclusive end date.')
    }
    setValidationError('')
    create.mutate({
      question: normalized,
      analysisType,
      preset,
      ...(preset === 'CUSTOM' ? { startDate, endDateExclusive } : {}),
    })
  }

  const active = detail.data

  return <>
    <PageHeader
      eyebrow="Evidence-based decision intelligence"
      title="Business insights"
      description="Investigate performance, test transparent scenarios, and save reports whose metrics and citations are server-verified."
    />
    <div className="insights-layout">
      <aside className="insights-history panel" aria-label="Business report history">
        <p className="eyebrow">Saved reports</p>
        {history.isPending ? <p className="muted">Loading reports…</p> : null}
        {history.isError ? <ErrorPanel error={history.error} onRetry={() => history.refetch()} /> : null}
        {history.data?.length ? <div className="insights-history-list">{history.data.map((item) =>
          <button
            key={item.id}
            type="button"
            className={selectedId === item.id ? 'insights-history-item insights-history-item--active' : 'insights-history-item'}
            onClick={() => setSelectedId(item.id)}
          >
            <span><strong>{item.title}</strong><small>{formatDate(item.createdAt)}</small></span>
            <em className={`report-status report-status--${item.status.toLowerCase()}`}>{item.status}</em>
          </button>)}</div> : !history.isPending && !history.isError ? <p className="muted">Generated reports will appear here.</p> : null}
      </aside>

      <div className="insights-main">
        <form className="insights-composer panel" onSubmit={submit}>
          <div className="insights-composer__heading"><div><p className="eyebrow">New investigation</p><h2>What decision do you need to understand?</h2></div><span>Read-only · advisory</span></div>
          <label htmlFor="business-question">Business question</label>
          <textarea
            id="business-question"
            rows={3}
            maxLength={1000}
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder="e.g. Why did revenue decline last month?"
          />
          <div className="insights-form-row">
            <label>Analysis type<select value={analysisType} onChange={(event) => setAnalysisType(event.target.value as BusinessAnalysisType)}>
              <option value="AUTO">Choose automatically</option>
              <option value="REVENUE_INVESTIGATION">Revenue investigation</option>
              <option value="INVENTORY_RISK">Inventory risk</option>
              <option value="SCENARIO_ANALYSIS">What-if scenario</option>
            </select></label>
            <label>Reporting period<select value={preset} onChange={(event) => setPreset(event.target.value as AnalyticsPreset)}>
              <option value="LAST_7_DAYS">Last 7 days</option>
              <option value="LAST_30_DAYS">Last 30 days</option>
              <option value="LAST_90_DAYS">Last 90 days</option>
              <option value="THIS_MONTH">This month</option>
              <option value="LAST_MONTH">Last month</option>
              <option value="CUSTOM">Custom</option>
            </select></label>
            {preset === 'CUSTOM' && <>
              <label>Start date<input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} /></label>
              <label>End date (exclusive)<input type="date" value={endDateExclusive} onChange={(event) => setEndDateExclusive(event.target.value)} /></label>
            </>}
          </div>
          <div className="insights-suggestions">{suggestions.map((item) =>
            <button key={item} type="button" onClick={() => setQuestion(item)}>{item}</button>)}</div>
          {validationError && <div className="notice notice--error" role="alert">{validationError}</div>}
          {create.isError && <ErrorPanel error={create.error} onRetry={() => {
            const form = document.querySelector<HTMLFormElement>('.insights-composer')
            form?.requestSubmit()
          }} />}
          <button className="button button--primary" type="submit" disabled={create.isPending || !question.trim()}>
            {create.isPending ? 'Generating verified report…' : 'Generate report'}
          </button>
          <small>Scenarios are mathematical assumptions, not forecasts. Recommendations never execute business changes.</small>
        </form>

        {detail.isPending ? <div className="panel insights-loading">Loading report…</div> : null}
        {detail.isError ? <ErrorPanel error={detail.error} onRetry={() => detail.refetch()} /> : null}
        {active?.metadata.status === 'FAILED' ? <div className="notice notice--error" role="alert">Report generation failed safely ({active.metadata.failureCode ?? 'unknown error'}). No completed report was fabricated.</div> : null}
        {active?.report ? <ReportView report={active.report} /> : !selectedId && !create.isPending ? <Welcome /> : null}
      </div>
    </div>
  </>
}

function Welcome() {
  return <section className="panel insights-empty"><span>◇</span><h2>Evidence before explanation</h2><p>Start an investigation to compare periods, decompose revenue changes, inspect inventory signals, or model a bounded scenario.</p></section>
}

function ReportView({ report }: { report: BusinessAnalysisReport }) {
  return <article className="business-report">
    <header className="business-report__header panel">
      <div><p className="eyebrow">{humanize(report.reportType)}</p><h2>{report.title}</h2><p>{report.summary}</p></div>
      <div className="report-meta"><span>Schema v{report.schemaVersion}</span><span>Report v{report.reportVersion}</span><span>Data as of {formatDate(report.dataAsOf)}</span></div>
    </header>

    <section className="business-metrics" aria-label="Verified metrics">{report.metrics.map((metric) => <Metric key={metric.key} metric={metric} />)}</section>

    <div className="business-chart-grid">{report.charts.filter((chart) => chart.points.length > 0).map((chart) => <ContributionChart key={chart.id} chart={chart} />)}</div>

    <section className="report-section report-section--verified panel">
      <SectionHeading label="Verified findings" note="Observed or deterministically calculated from paid orders" />
      <div className="finding-list">{report.findings.map((finding) => <div key={finding.id} className="finding-card"><span>{humanize(finding.kind)}</span><h3>{finding.title}</h3><p>{finding.statement}</p>{finding.calculationDefinition && <code>{finding.calculationDefinition}</code>}<EvidenceLinks ids={finding.evidenceIds} report={report} /></div>)}</div>
    </section>

    <section className="report-section report-section--hypothesis panel">
      <SectionHeading label="Hypotheses" note="Possible explanations that require further validation" />
      {report.hypotheses.length ? report.hypotheses.map((item) => <div className="hypothesis-card" key={item.id}><span>{item.status}</span><h3>{item.statement}</h3><p>{item.rationale}</p><strong>Validation plan</strong><p>{item.validationPlan}</p><EvidenceLinks ids={item.supportingEvidenceIds} report={report} /></div>) : <p className="muted">No supported hypothesis was generated from the available evidence.</p>}
    </section>

    {report.scenarios.length > 0 && <section className="report-section report-section--scenario panel">
      <SectionHeading label="What-if scenarios" note="Transparent calculations—not predictions" />
      <div className="scenario-grid">{report.scenarios.map((item) => <div key={item.id} className="scenario-card"><span>{humanize(item.type)}</span><strong>{displayValue(item.baselineValue, item.unit)} → {displayValue(item.modeledValue, item.unit)}</strong>{item.modeledGrossProfit != null && <p>Modeled gross profit: {displayValue(item.baselineGrossProfit, item.unit)} → {displayValue(item.modeledGrossProfit, item.unit)} ({displayValue(item.grossProfitChange, item.unit)} change)</p>}<p>{item.statement}</p><small>{item.changePercent >= 0 ? '+' : ''}{item.changePercent}% input</small>{item.assumptions.map((text) => <p key={text} className="muted">Assumption: {text}</p>)}{item.limitations.map((text) => <p key={text} className="warning-text">{text}</p>)}</div>)}</div>
    </section>}

    <section className="report-section report-section--recommendation panel">
      <SectionHeading label="Recommendations" note="Advisory only; no operation is executed" />
      <div className="recommendation-list">{report.recommendations.map((item) => <div key={item.id} className="recommendation-card"><div><span className={`priority priority--${item.priority.toLowerCase()}`}>{item.priority}</span><small>{item.approvalRequired ? 'Human approval required' : 'Advisory'}</small></div><h3>{item.title}</h3><p>{item.description}</p><p className="muted">{item.rationale}</p><strong>Suggested next steps</strong><ol>{item.nextSteps.map((step) => <li key={step}>{step}</li>)}</ol>{item.expectedImpact == null && <p className="muted">Estimated financial impact: unavailable</p>}<EvidenceLinks ids={item.evidenceIds} report={report} /></div>)}</div>
    </section>

    <section className="report-section report-section--limitations panel">
      <SectionHeading label="Limitations and uncertainty" note="Known boundaries on interpretation" />
      <ul>{report.limitations.map((item) => <li key={item.code}><strong>{humanize(item.code)}</strong><span>{item.description}</span></li>)}</ul>
    </section>

    <details className="panel report-evidence"><summary>Evidence ledger and model metadata</summary>
      <div className="evidence-table">{report.evidence.map((item) => <div id={`evidence-${item.id}`} key={item.id}><strong>{humanize(item.metricKey)}</strong><span>{displayValue(item.value, item.unit)}</span><small>{item.source} · {item.periodStart} to {item.periodEndExclusive} · observed {formatDate(item.observedAt)}</small></div>)}</div>
      <p className="muted">Provider: {report.modelMetadata.provider} / {report.modelMetadata.model} · Prompt: {report.modelMetadata.promptVersion} · Tokens: {report.modelMetadata.totalTokens ?? 'unavailable'} · Estimated cost: {report.modelMetadata.estimatedCostUsd == null ? 'unavailable' : `$${report.modelMetadata.estimatedCostUsd.toFixed(6)}`} (not billed cost)</p>
    </details>
  </article>
}

function Metric({ metric }: { metric: BusinessMetric }) {
  return <div className="business-metric panel"><span>{metric.label}</span><strong>{displayValue(metric.value, metric.unit)}</strong><small>{metric.changePercent == null ? 'No comparable baseline' : <em className={metric.changePercent >= 0 ? 'delta-positive' : 'delta-negative'}>{metric.changePercent >= 0 ? '+' : ''}{metric.changePercent.toFixed(1)}% vs prior</em>}</small></div>
}

function ContributionChart({ chart }: { chart: BusinessChart }) {
  return <section className="panel contribution-chart"><h3>{chart.title}</h3><div className="chart"><ResponsiveContainer width="100%" height="100%"><BarChart data={chart.points} layout="vertical" margin={{ left: 20, right: 20 }}><CartesianGrid strokeDasharray="3 3" horizontal={false} /><XAxis type="number" /><YAxis type="category" dataKey="label" width={110} tick={{ fontSize: 10 }} /><Tooltip formatter={(value) => displayValue(Number(value), chart.unit)} /><Bar dataKey="value" name={chart.valueLabel} radius={[0, 4, 4, 0]}>{chart.points.map((point) => <Cell key={point.key} fill={point.value >= 0 ? '#6f914f' : '#b86557'} />)}</Bar></BarChart></ResponsiveContainer></div></section>
}

function SectionHeading({ label, note }: { label: string; note: string }) {
  return <header className="report-section__heading"><div><h2>{label}</h2><p>{note}</p></div></header>
}

function EvidenceLinks({ ids, report }: { ids: string[]; report: BusinessAnalysisReport }) {
  return <div className="evidence-links">{ids.map((id) => {
    const item = report.evidence.find((candidate) => candidate.id === id)
    return <a key={id} href={`#evidence-${id}`}>Evidence: {item ? humanize(item.metricKey) : id}</a>
  })}</div>
}

function displayValue(value: number | null, unit: string) {
  if (value == null) return 'Unavailable'
  if (/^[A-Z]{3}$/.test(unit)) return new Intl.NumberFormat(undefined, { style: 'currency', currency: unit }).format(value)
  return `${new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value)}${unit === 'COUNT' ? '' : ` ${unit.toLowerCase()}`}`
}

function formatDate(value: string) {
  return new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

function humanize(value: string) {
  return value.replaceAll('.', ' · ').replaceAll('_', ' ').toLowerCase().replace(/^./, (letter) => letter.toUpperCase())
}
