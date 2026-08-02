import { useEffect, useState, type ReactNode } from 'react'
import { API_BASE, type AiStatus } from './api'

export function AuthShell({
  title,
  subtitle,
  children,
}: {
  title: string
  subtitle: string
  children: ReactNode
}) {
  const [aiStatus, setAiStatus] = useState<AiStatus | null>(null)

  useEffect(() => {
    let cancelled = false
    void fetch(`${API_BASE}/api/health/ai`)
      .then((res) => (res.ok ? res.json() : Promise.reject()))
      .then((data: AiStatus) => {
        if (!cancelled) setAiStatus(data)
      })
      .catch(() => {
        /* ignore — auth still works offline */
      })
    return () => {
      cancelled = true
    }
  }, [])

  const tone = aiStatus?.ok ? 'ok' : aiStatus?.configured ? 'warn' : aiStatus ? 'off' : 'loading'

  return (
    <div className="auth-shell">
      <aside className="auth-brand">
        <div className="auth-brand-inner">
          <p className="brand-mark">SelfSync</p>
          <p className="brand-tagline">Practice interviews that actually prepare you.</p>
          <p>Adaptive questions, voice and video answers, and feedback that maps to what you need next.</p>
          <div className={`ai-status-chip tone-${tone}`}>
            <span className="ai-status-dot" />
            {aiStatus?.ok
              ? `Groq AI online · ${aiStatus.model}`
              : aiStatus
                ? `Groq: ${aiStatus.ok ? 'online' : 'check failed'}`
                : 'Checking Groq…'}
          </div>
        </div>
      </aside>
      <div className="auth-form-side">
        <div className="auth-form-panel">
          <p className="brand-mark mobile-only">SelfSync</p>
          <h1>{title}</h1>
          <p className="muted">{subtitle}</p>
          {children}
        </div>
      </div>
    </div>
  )
}
