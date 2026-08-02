import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiRequest, type AnalyticsSummary, type InterviewSummary } from '../api'
import { useAuth } from '../auth'
import { allDomains } from '../interviewOptions'

export function AnalyticsPage() {
  const { token } = useAuth()
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null)
  const [sessions, setSessions] = useState<InterviewSummary[]>([])
  const [domain, setDomain] = useState('')
  const [status, setStatus] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    if (!token) return
    void apiRequest<AnalyticsSummary>('/api/analytics/summary', { token })
      .then(setSummary)
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load analytics'))
  }, [token])

  useEffect(() => {
    if (!token) return
    const params = new URLSearchParams()
    if (domain) params.set('domain', domain)
    if (status) params.set('status', status)
    const qs = params.toString()
    void apiRequest<InterviewSummary[]>(`/api/interviews${qs ? `?${qs}` : ''}`, { token })
      .then(setSessions)
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load history'))
  }, [token, domain, status])

  if (error) return <section className="panel"><p className="error">{error}</p></section>
  if (!summary) return <section className="panel"><p>Loading analytics…</p></section>

  return (
    <section className="panel analytics-panel">
      <header className="section-head">
        <p className="eyebrow">Your trajectory</p>
        <h1>Performance analytics</h1>
        <p className="muted">Track progress across domains and sessions.</p>
      </header>

      <div className="score-grid">
        <div className="score-tile"><span>Sessions</span><strong>{summary.totalSessions}</strong></div>
        <div className="score-tile"><span>Completed</span><strong>{summary.completedSessions}</strong></div>
        <div className="score-tile"><span>Avg overall</span><strong>{summary.averageOverallScore.toFixed(1)}</strong></div>
        <div className="score-tile"><span>Streak (days)</span><strong>{summary.currentStreakDays ?? 0}</strong></div>
        <div className="score-tile"><span>Weekly goal</span><strong>{summary.weeklyCompleted ?? 0}/{summary.weeklyGoalTarget ?? 3}</strong></div>
        <div className="score-tile"><span>Avg technical</span><strong>{summary.averageTechnical.toFixed(1)}</strong></div>
        <div className="score-tile"><span>Avg communication</span><strong>{summary.averageCommunication.toFixed(1)}</strong></div>
        <div className="score-tile"><span>Avg confidence</span><strong>{summary.averageConfidence.toFixed(1)}</strong></div>
      </div>
      <p className="muted small">
        Tip: open <Link to="/app/learning">Learning</Link> for content mapped to your weak areas.
      </p>

      <h2>Domain trends</h2>
      {summary.domainTrends.length === 0 ? (
        <p className="muted">Complete interviews to see domain averages.</p>
      ) : (
        <ul className="trend-list">
          {summary.domainTrends.map((t) => (
            <li key={t.domain}>
              <strong>{t.domain}</strong>
              <span>{t.sessions} sessions</span>
              <span>avg {t.averageScore.toFixed(1)}</span>
            </li>
          ))}
        </ul>
      )}

      <h2>Session history</h2>
      <div className="filters">
        <select value={domain} onChange={(e) => setDomain(e.target.value)}>
          <option value="">All domains</option>
          {allDomains().map((d) => (
            <option key={d} value={d}>{d}</option>
          ))}
        </select>
        <select value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="">All statuses</option>
          <option value="InProgress">In progress</option>
          <option value="Completed">Completed</option>
        </select>
      </div>

      <ul className="history-list">
        {sessions.map((s) => (
          <li key={s.id}>
            <div>
              <strong>
                {s.domain} · {s.interviewType}
              </strong>
              <p className="muted small">
                {s.difficulty} · {s.status} · {new Date(s.createdAt).toLocaleString()}
              </p>
            </div>
            <div className="history-actions">
              {s.overallScore != null && <span className="badge">{s.overallScore.toFixed(1)}</span>}
              <Link to={s.status === 'Completed' ? `/app/results/${s.id}` : `/app/interview/${s.id}`}>
                Open
              </Link>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
