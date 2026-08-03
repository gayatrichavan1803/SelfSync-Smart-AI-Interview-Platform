import { useEffect, useState } from 'react'
import { apiRequest, type LearningRecommendations } from '../api'
import { useAuth } from '../auth'

export function LearningPage() {
  const { token } = useAuth()
  const [data, setData] = useState<LearningRecommendations | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!token) return
    void apiRequest<LearningRecommendations>('/api/learning/recommendations', { token })
      .then(setData)
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load recommendations'))
  }, [token])

  if (error) return <section className="panel"><p className="error">{error}</p></section>
  if (!data) return <section className="panel"><p>Loading personalized learning…</p></section>

  return (
    <section className="panel">
      <header className="section-head">
        <p className="eyebrow">Growth path</p>
        <h1>Learning recommendations</h1>
        <p className="muted">Personalized resources mapped from your interview weaknesses.</p>
      </header>
      <h2>Focus skills</h2>
      <div className="choice-row">
        {data.focusSkills.map((s) => (
          <span key={s} className="badge">{s}</span>
        ))}
      </div>
      <h2>Recommended content</h2>
      <ul className="history-list">
        {data.resources.map((r) => (
          <li key={`${r.title}-${r.url}`}>
            <div>
              <strong>{r.title}</strong>
              <p className="muted small">
                {r.skill} · {r.level}
              </p>
              <p>{r.description}</p>
            </div>
            <a href={r.url} target="_blank" rel="noreferrer">
              Open
            </a>
          </li>
        ))}
      </ul>
    </section>
  )
}
