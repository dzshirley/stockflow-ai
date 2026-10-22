import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Bar, BarChart, CartesianGrid, Legend, Line, LineChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import { PageHeader } from '../components/AppShell'
import { ErrorPanel } from '../components/Feedback'
import { getAiConversation, listAiConversations, queryAi } from '../services/ai'
import type { AiChart, AiConversationMessage, AiMetric, AiResponse } from '../types/api'

const suggestions = [
  'Compare revenue with the previous 30 days',
  'Show a weekly revenue trend for the last 90 days',
  'What are my top 5 products this month?',
  'Which products are at inventory risk?',
]

export function AiCopilotPage() {
  const queryClient = useQueryClient()
  const [conversationId, setConversationId] = useState<string>()
  const [messages, setMessages] = useState<AiConversationMessage[]>([])
  const [question, setQuestion] = useState('')
  const conversations = useQuery({ queryKey: ['ai', 'conversations'], queryFn: () => listAiConversations() })
  const loadConversation = useMutation({
    mutationFn: (id: string) => getAiConversation(id),
    onSuccess: (data) => setMessages(data.messages),
  })

  const send = useMutation({
    mutationFn: (text: string) => queryAi(text, conversationId),
    onSuccess: (response) => {
      setConversationId(response.conversationId)
      setMessages((current) => [...current, assistantMessage(response)])
      void queryClient.invalidateQueries({ queryKey: ['ai', 'conversations'] })
    },
  })

  function submit(event: React.FormEvent) {
    event.preventDefault()
    const text = question.trim()
    if (!text || send.isPending) return
    setMessages((current) => [...current, userMessage(text)])
    setQuestion('')
    send.mutate(text)
  }

  function startNew() {
    setConversationId(undefined)
    setMessages([])
    setQuestion('')
    send.reset()
    loadConversation.reset()
  }

  function selectConversation(id: string) {
    setConversationId(id)
    setMessages([])
    send.reset()
    loadConversation.mutate(id)
  }

  const activeTitle = useMemo(
    () => conversations.data?.find((item) => item.id === conversationId)?.title,
    [conversations.data, conversationId],
  )

  return <>
    <PageHeader eyebrow="Verified AI analytics" title="AI data copilot" description="Ask natural-language questions. Every number comes from an allow-listed, store-scoped analytics query." />
    <div className="ai-layout">
      <aside className="ai-history panel" aria-label="Conversation history">
        <button className="button button--primary ai-new" type="button" onClick={startNew}>+ New analysis</button>
        <p className="eyebrow">Recent conversations</p>
        {conversations.isPending ? <p className="muted">Loading history…</p> : conversations.isError ? <p className="muted">History unavailable</p> :
          conversations.data?.length ? <div className="ai-history-list">{conversations.data.map((item) =>
            <button key={item.id} type="button" className={item.id === conversationId ? 'ai-history-item ai-history-item--active' : 'ai-history-item'} onClick={() => selectConversation(item.id)}>
              <strong>{item.title}</strong><small>{new Date(item.updatedAt).toLocaleDateString()}</small>
            </button>)}</div> : <p className="muted">Your analyses will appear here.</p>}
      </aside>

      <section className="ai-chat panel" aria-label="AI analytics conversation">
        <header className="ai-chat-header"><div><span className="ai-status">● verified data tools</span><h2>{activeTitle ?? 'New analysis'}</h2></div><span className="ai-provider">Provider: {lastResponse(messages)?.provider ?? 'mock'}</span></header>
        <div className="ai-thread" aria-live="polite">
          {!messages.length && !loadConversation.isPending ? <Welcome onPrompt={(prompt) => setQuestion(prompt)} /> : null}
          {loadConversation.isPending ? <p className="ai-thinking">Loading conversation…</p> : null}
          {loadConversation.isError ? <ErrorPanel error={loadConversation.error} onRetry={() => conversationId && loadConversation.mutate(conversationId)} /> : null}
          {messages.map((message) => <Message key={message.id} message={message} />)}
          {send.isPending ? <div className="ai-message ai-message--assistant ai-thinking">Checking verified analytics data…</div> : null}
          {send.isError ? <div className="notice notice--error" role="alert">The analysis could not be completed. {send.error instanceof Error ? send.error.message : ''}<button className="button button--ghost" type="button" onClick={() => send.mutate(messages.filter((item) => item.role === 'USER').at(-1)?.content ?? '')}>Retry</button></div> : null}
        </div>
        <form className="ai-composer" onSubmit={submit}>
          <label htmlFor="ai-question">Ask about your business data</label>
          <div><textarea id="ai-question" rows={2} maxLength={1000} value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="e.g. Compare revenue with the previous 30 days" /><button className="button button--primary" disabled={!question.trim() || send.isPending}>Analyze</button></div>
          <small>Read-only analytics · Store-scoped · AI can make mistakes; metrics are server-verified.</small>
        </form>
      </section>
    </div>
  </>
}

function Welcome({ onPrompt }: { onPrompt: (prompt: string) => void }) {
  return <div className="ai-welcome"><span className="ai-welcome-mark">✦</span><h2>What would you like to understand?</h2><p>Ask about revenue, orders, sales trends, products, categories, or inventory.</p><div className="ai-suggestions">{suggestions.map((prompt) => <button key={prompt} type="button" onClick={() => onPrompt(prompt)}>{prompt}</button>)}</div></div>
}

function Message({ message }: { message: AiConversationMessage }) {
  if (message.role === 'USER') return <div className="ai-message ai-message--user"><span>You</span><p>{message.content}</p></div>
  return <div className="ai-message ai-message--assistant"><span>StockFlow analyst</span>{message.response ? <Response response={message.response} /> : <p>{message.content}</p>}</div>
}

function Response({ response }: { response: AiResponse }) {
  return <div className="ai-response">
    <p className="ai-summary">{response.summary}</p>
    {response.metrics.length > 0 && <div className="ai-metrics">{response.metrics.map((metric) => <MetricCard key={metric.key} metric={metric} />)}</div>}
    {response.charts.map((chart) => <ChartPanel key={chart.id} chart={chart} />)}
    {response.tables.map((table) => <section key={table.id} className="ai-result-panel"><h3>{table.title}</h3><div className="table-wrap"><table><thead><tr>{table.columns.map((column) => <th key={column}>{humanize(column)}</th>)}</tr></thead><tbody>{table.rows.map((row, index) => <tr key={index}>{table.columns.map((column) => <td key={column}>{display(row[column])}</td>)}</tr>)}</tbody></table></div></section>)}
    <details className="ai-details"><summary>Evidence, filters, and limitations</summary>
      <div className="ai-detail-grid"><div><h4>Verified evidence</h4>{response.evidence.map((item) => <p key={item.toolExecutionId}><strong>{item.description}</strong><br />{item.period.startDate} to {item.period.endDateExclusive} · {item.period.timeZone}</p>)}</div><div><h4>Applied filters</h4>{response.appliedFilters.map((item) => <p key={`${item.name}-${item.value}`}>{humanize(item.name)}: <strong>{item.value}</strong></p>)}</div></div>
      {response.limitations.map((item) => <p className="ai-limitation" key={item}>{item}</p>)}
    </details>
  </div>
}

function MetricCard({ metric }: { metric: AiMetric }) {
  return <div className="ai-metric"><span>{metric.label}</span><strong>{formatMetric(metric.value, metric.unit)}</strong>{metric.comparable && metric.changePercent != null ? <small className={metric.changePercent >= 0 ? 'delta-positive' : 'delta-negative'}>{metric.changePercent >= 0 ? '+' : ''}{metric.changePercent.toFixed(1)}% vs previous</small> : <small>{metric.unit}</small>}</div>
}

function ChartPanel({ chart }: { chart: AiChart }) {
  const Chart = chart.type === 'LINE' ? LineChart : BarChart
  return <section className="ai-result-panel"><h3>{chart.title}</h3><div className="chart chart--compact"><ResponsiveContainer width="100%" height="100%"><Chart data={chart.data} margin={{ top: 10, right: 10, left: 0, bottom: 10 }}><CartesianGrid strokeDasharray="3 3" vertical={false} /><XAxis dataKey={chart.xKey} tick={{ fontSize: 10 }} /><YAxis tick={{ fontSize: 10 }} /><Tooltip /><Legend />{chart.series.map((series, index) => chart.type === 'LINE' ? <Line key={series.key} dataKey={series.key} name={series.label} stroke={index ? '#80958a' : '#527a43'} strokeWidth={2} dot={false} /> : <Bar key={series.key} dataKey={series.key} name={series.label} fill={index ? '#8ca295' : '#6f914f'} radius={[4, 4, 0, 0]} />)}</Chart></ResponsiveContainer></div></section>
}

function assistantMessage(response: AiResponse): AiConversationMessage {
  return { id: response.messageId, role: 'ASSISTANT', content: response.summary, createdAt: new Date().toISOString(), response }
}

function userMessage(content: string): AiConversationMessage {
  return { id: `pending-${Date.now()}`, role: 'USER', content, createdAt: new Date().toISOString(), response: null }
}

function lastResponse(messages: AiConversationMessage[]) {
  return messages.map((item) => item.response).filter(Boolean).at(-1)
}

function humanize(value: string) {
  return value.replaceAll('.', ' · ').replace(/([a-z])([A-Z])/g, '$1 $2').replaceAll('_', ' ')
}

function display(value: string | number | null | undefined) {
  if (value == null) return '—'
  return typeof value === 'number' ? new Intl.NumberFormat().format(value) : value
}

function formatMetric(value: number, unit: string) {
  if (/^[A-Z]{3}$/.test(unit)) return new Intl.NumberFormat(undefined, { style: 'currency', currency: unit }).format(value)
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value)
}
