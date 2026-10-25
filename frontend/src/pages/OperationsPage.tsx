import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { PageHeader } from '../components/AppShell'
import { ErrorPanel } from '../components/Feedback'
import { StatusBadge } from '../components/StatusBadge'
import { useAuth } from '../features/auth/AuthProvider'
import { getBusinessReport, listBusinessReports } from '../services/businessAnalysis'
import {
  approveRestockProposal,
  cancelRestockProposal,
  executeRestockProposal,
  generateRestockProposals,
  getRestockProposalHistory,
  listRestockProposals,
  rejectRestockProposal,
} from '../services/restockProposals'
import type { RestockProposal } from '../types/api'
import { newIdempotencyKey, newUuid } from '../utils/format'

const prompt = 'Analyze our inventory and prepare a restock proposal for products at risk of running out.'

export function OperationsPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [selectedId, setSelectedId] = useState<string>()
  const [reportId, setReportId] = useState('')
  const [recommendationId, setRecommendationId] = useState('')
  const [question, setQuestion] = useState(prompt)
  const [reason, setReason] = useState('')
  const [receiptConfirmed, setReceiptConfirmed] = useState(false)
  const generateKey = useRef(newIdempotencyKey())
  const executeKey = useRef(newUuid())

  const proposals = useQuery({ queryKey: ['restock-proposals'], queryFn: () => listRestockProposals() })
  const reports = useQuery({
    queryKey: ['business-analysis', 'reports', 'operations'],
    queryFn: () => listBusinessReports({ status: 'COMPLETED', limit: 50 }),
  })
  const source = useQuery({
    queryKey: ['business-analysis', 'report', reportId],
    queryFn: () => getBusinessReport(reportId), enabled: Boolean(reportId),
  })
  const selected = proposals.data?.find((item) => item.id === selectedId) ?? proposals.data?.[0]
  const sourceForSelected = useQuery({
    queryKey: ['business-analysis', 'report', selected?.sourceReportId, 'operations'],
    queryFn: () => getBusinessReport(selected!.sourceReportId), enabled: Boolean(selected),
  })
  const history = useQuery({
    queryKey: ['restock-proposals', selected?.id, 'history'],
    queryFn: () => getRestockProposalHistory(selected!.id), enabled: Boolean(selected),
  })
  const recommendations = source.data?.report?.recommendations.filter((item) =>
    item.approvalRequired && item.evidenceIds.some((id) => source.data?.report?.evidence
      .some((evidence) => evidence.id === id && evidence.source === 'INVENTORY_SNAPSHOT')),
  ) ?? []
  const effectiveRecommendationId = recommendationId || recommendations[0]?.id || ''
  const selectedEvidence = sourceForSelected.data?.report?.evidence.find((item) => item.id === selected?.evidenceId)

  const refresh = async (proposal?: RestockProposal) => {
    if (proposal) setSelectedId(proposal.id)
    await queryClient.invalidateQueries({ queryKey: ['restock-proposals'] })
    if (proposal) await queryClient.invalidateQueries({ queryKey: ['restock-proposals', proposal.id, 'history'] })
  }
  const generate = useMutation({
    mutationFn: () => generateRestockProposals({ question: question.trim(), sourceReportId: reportId, recommendationId: effectiveRecommendationId }, generateKey.current),
    onSuccess: async (items) => { generateKey.current = newIdempotencyKey(); await refresh(items[0]) },
  })
  const approve = useMutation({ mutationFn: (item: RestockProposal) => approveRestockProposal(item.id, item.version), onSuccess: refresh })
  const reject = useMutation({ mutationFn: (item: RestockProposal) => rejectRestockProposal(item.id, item.version, reason), onSuccess: async (item) => { setReason(''); await refresh(item) } })
  const cancel = useMutation({ mutationFn: (item: RestockProposal) => cancelRestockProposal(item.id, item.version, reason), onSuccess: async (item) => { setReason(''); await refresh(item) } })
  const execute = useMutation({
    mutationFn: (item: RestockProposal) => executeRestockProposal(item.id, item.version, executeKey.current),
    onSuccess: async (item) => { executeKey.current = newUuid(); setReceiptConfirmed(false); await refresh(item) },
    onError: () => { void refresh(selected) },
  })
  const actionError = [generate, approve, reject, cancel, execute].find((mutation) => mutation.isError)?.error
  const busy = [generate, approve, reject, cancel, execute].some((mutation) => mutation.isPending)

  return <>
    <PageHeader eyebrow="Human-approved AI operations" title="Restock operations" description="Turn verified inventory evidence into reviewable proposals. Inventory changes only after an ADMIN confirms physical receipt." />

    <section className="operations-generator panel">
      <div><p className="eyebrow">Read-only proposal agent</p><h2>Prepare from a completed report</h2><p>The model can select only server-authorized risk candidates. Stock quantities are calculated deterministically.</p></div>
      <label>Source report<select value={reportId} onChange={(event) => { setReportId(event.target.value); setRecommendationId('') }}>
        <option value="">Choose a completed report</option>
        {reports.data?.map((item) => <option key={item.id} value={item.id}>{item.title} · {formatDate(item.createdAt)}</option>)}
      </select></label>
      <label>Evidence-backed recommendation<select value={effectiveRecommendationId} onChange={(event) => setRecommendationId(event.target.value)} disabled={!reportId || source.isPending}>
        <option value="">Choose an inventory recommendation</option>
        {recommendations.map((item) => <option key={item.id} value={item.id}>{item.title}</option>)}
      </select></label>
      <label>Request<textarea rows={2} maxLength={1000} value={question} onChange={(event) => setQuestion(event.target.value)} /></label>
      <button className="button button--primary" disabled={!reportId || !effectiveRecommendationId || !question.trim() || busy} onClick={() => generate.mutate()}>
        {generate.isPending ? 'Preparing proposal…' : 'Generate proposal'}
      </button>
    </section>

    {actionError && <ErrorPanel error={actionError} />}
    <div className="operations-layout">
      <aside className="panel operations-list" aria-label="Restock proposals">
        <p className="eyebrow">Proposal queue</p>
        {proposals.isPending && <p className="muted">Loading proposals…</p>}
        {proposals.isError && <ErrorPanel error={proposals.error} onRetry={() => proposals.refetch()} />}
        {!proposals.isPending && !proposals.data?.length && <p className="muted">No proposals yet. Generate one from a verified report.</p>}
        {proposals.data?.map((item) => <button key={item.id} className={selected?.id === item.id ? 'operations-list-item operations-list-item--active' : 'operations-list-item'} onClick={() => setSelectedId(item.id)}>
          <span><strong>{item.productName}</strong><small>{item.sku} · {item.proposedQuantity} units</small></span><StatusBadge status={item.status} />
        </button>)}
      </aside>

      <main className="operations-detail">
        {!selected ? <section className="panel empty-state"><span className="empty-state__mark">OP</span><h3>No proposal selected</h3><p>Proposal evidence, approval controls, freshness warnings, and execution history appear here.</p></section> : <>
          {(selected.status === 'STALE' || selected.status === 'FAILED') && <div className="notice notice--warning" role="alert">
            {selected.status === 'STALE' ? `Data changed (${selected.staleReason ?? 'unknown reason'}). Generate a new proposal.` : `Execution failed safely (${selected.failureCode ?? 'unknown error'}). No partial receipt was recorded.`}
          </div>}
          <section className="panel proposal-hero">
            <div><StatusBadge status={selected.status} /><p className="eyebrow">{selected.sku}</p><h2>Receive {selected.proposedQuantity} units of {selected.productName}</h2><p>{selected.rationale}</p></div>
            <dl className="proposal-numbers"><div><dt>Available</dt><dd>{selected.snapshotAvailable}</dd></div><div><dt>Reserved</dt><dd>{selected.snapshotReserved}</dd></div><div><dt>Reorder point</dt><dd>{selected.snapshotReorderPoint}</dd></div><div><dt>Proposed receipt</dt><dd>+{selected.proposedQuantity}</dd></div></dl>
          </section>
          <div className="proposal-grid">
            <section className="panel proposal-evidence"><h3>Evidence and assumptions</h3>
              {selectedEvidence ? <div className="evidence-callout"><strong>{selectedEvidence.filters.riskLevel?.replaceAll('_', ' ')}</strong><span>{selectedEvidence.metricKey}</span><p>{selectedEvidence.value} units observed · {selectedEvidence.periodStart} to {selectedEvidence.periodEndExclusive}</p></div> : <p className="muted">Loading source evidence…</p>}
              <ul>{selected.assumptions.map((item) => <li key={item}>{item}</li>)}</ul>
              <p className="muted">Source report v{selected.sourceReportVersion} · Data as of {formatDate(selected.sourceDataAsOf)} · Inventory version {selected.snapshotInventoryVersion}</p>
            </section>
            <section className="panel proposal-actions"><h3>Human approval and receipt</h3>
              {user?.role === 'ADMIN' && selected.status === 'PROPOSED' && <><p>Approval records this exact proposal version. It does not increase inventory.</p><button className="button button--primary" disabled={busy} onClick={() => approve.mutate(selected)}>Approve proposal</button><label>Decision note<input value={reason} maxLength={500} onChange={(event) => setReason(event.target.value)} placeholder="Optional review note" /></label><button className="button button--danger" disabled={busy} onClick={() => reject.mutate(selected)}>Reject</button></>}
              {selected.status === 'PROPOSED' && <button className="button button--ghost" disabled={busy} onClick={() => cancel.mutate(selected)}>Cancel proposal</button>}
              {user?.role === 'ADMIN' && (selected.status === 'APPROVED' || selected.status === 'FAILED') && <>
                <div className="notice notice--warning">Approval is not a purchase order. Continue only after the listed stock has physically arrived.</div>
                <label className="receipt-check"><input type="checkbox" checked={receiptConfirmed} onChange={(event) => setReceiptConfirmed(event.target.checked)} /><span>I confirm {selected.proposedQuantity} units were physically received.</span></label>
                <button className="button button--primary" disabled={!receiptConfirmed || busy} onClick={() => execute.mutate(selected)}>{execute.isPending ? 'Recording receipt…' : 'Record received inventory'}</button>
              </>}
              {user?.role !== 'ADMIN' && <p className="muted">ADMIN approval and execution are required. You can monitor proposals you created.</p>}
              {selected.status === 'EXECUTED' && <div className="notice notice--info">Receipt recorded at {formatDate(selected.executedAt!)}. Adjustment: {selected.inventoryAdjustmentId}</div>}
            </section>
          </div>
          <section className="panel proposal-history"><h3>Execution history</h3>
            {history.isPending ? <p className="muted">Loading history…</p> : <ol>{history.data?.events.map((event) => <li key={event.id}><span>{formatDate(event.createdAt)}</span><strong>{event.action.replaceAll('_', ' ')}</strong><small>{event.fromStatus ?? 'START'} → {event.toStatus}{event.reasonCode ? ` · ${event.reasonCode}` : ''}</small></li>)}</ol>}
            {history.data?.attempts.map((attempt) => <div className="attempt-row" key={attempt.id}><strong>Attempt {attempt.attemptNumber}: {attempt.status}</strong><span>Command {attempt.commandId}</span>{attempt.errorCode && <small>{attempt.errorCode}</small>}</div>)}
          </section>
        </>}
      </main>
    </div>
  </>
}

function formatDate(value: string) {
  return new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}
