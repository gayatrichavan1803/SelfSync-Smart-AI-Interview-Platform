import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { API_BASE, apiRequest, type AiStatus, type InterviewSession } from '../api'
import { useAuth } from '../auth'
import {
  DIFFICULTIES,
  DOMAINS_BY_TYPE,
  INTERVIEW_TYPES,
  domainsForType,
} from '../interviewOptions'

export function HomePage() {
  const { token, user } = useAuth()
  const navigate = useNavigate()
  const [interviewType, setInterviewType] = useState<string>('Technical')
  const [domain, setDomain] = useState<string>(DOMAINS_BY_TYPE.Technical[0])
  const [difficulty, setDifficulty] = useState<string>('Medium')
  const [error, setError] = useState('')
  const [starting, setStarting] = useState(false)
  const [aiStatus, setAiStatus] = useState<AiStatus | null>(null)
  const [aiStatusError, setAiStatusError] = useState('')

  const domains = useMemo(() => domainsForType(interviewType), [interviewType])

  useEffect(() => {
    let cancelled = false
    void fetch(`${API_BASE}/api/health/ai`)
      .then(async (res) => {
        if (!res.ok) throw new Error('Could not reach AI health endpoint')
        return (await res.json()) as AiStatus
      })
      .then((data) => {
        if (!cancelled) setAiStatus(data)
      })
      .catch((err) => {
        if (!cancelled) {
          setAiStatusError(err instanceof Error ? err.message : 'AI status unavailable')
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  function selectType(nextType: string) {
    setInterviewType(nextType)
    const nextDomains = domainsForType(nextType)
    setDomain(nextDomains[0] ?? '')
  }

  async function onStart(e: FormEvent) {
    e.preventDefault()
    if (!token) return
    if (!domains.includes(domain)) {
      setError('Pick a domain that matches the interview type.')
      return
    }
    setError('')
    setStarting(true)
    try {
      const session = await apiRequest<InterviewSession>('/api/interviews', {
        method: 'POST',
        token,
        body: JSON.stringify({ interviewType, domain, difficulty }),
      })
      navigate(`/app/interview/${session.id}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not start interview')
    } finally {
      setStarting(false)
    }
  }

  const firstName = user?.fullName?.split(' ')[0] ?? 'there'
  const statusTone = aiStatus?.ok ? 'ok' : aiStatus?.configured ? 'warn' : aiStatus ? 'off' : 'loading'

  return (
    <>
      <section className={`ai-status-banner tone-${statusTone}`} aria-live="polite">
        <div className="ai-status-dot" />
        <div>
          <strong>
            {statusTone === 'ok' && 'Groq AI online'}
            {statusTone === 'warn' && 'Groq configured but not responding'}
            {statusTone === 'off' && 'Groq AI offline'}
            {statusTone === 'loading' && 'Checking Groq AI…'}
          </strong>
          <p className="muted small">
            {aiStatus
              ? `${aiStatus.provider} · ${aiStatus.model} — ${aiStatus.message}`
              : aiStatusError || 'Verifying API key…'}
          </p>
        </div>
      </section>

      <section className="panel start-panel">
        <header className="section-head">
          <div className="start-hero-line">
            <span className="hi">Hi {firstName}</span>
            <p className="eyebrow">Mock interview studio</p>
          </div>
          <h1>Start a mock interview</h1>
          <p className="muted">
            Choose a type first — domains update to match. Each session generates a fresh set of questions.
          </p>
        </header>
        {error && <p className="error">{error}</p>}
        <form className="start-form" onSubmit={onStart}>
          <fieldset>
            <legend>Interview type</legend>
            <div className="choice-row">
              {INTERVIEW_TYPES.map((t) => (
                <button
                  key={t}
                  type="button"
                  className={interviewType === t ? 'choice active' : 'choice'}
                  onClick={() => selectType(t)}
                >
                  {t}
                </button>
              ))}
            </div>
          </fieldset>
          <fieldset>
            <legend>Domain for {interviewType}</legend>
            <p className="muted small" style={{ margin: '0 0 0.65rem' }}>
              Only domains that fit {interviewType} are shown.
            </p>
            <div className="choice-row">
              {domains.map((d) => (
                <button
                  key={d}
                  type="button"
                  className={domain === d ? 'choice active' : 'choice'}
                  onClick={() => setDomain(d)}
                >
                  {d}
                </button>
              ))}
            </div>
          </fieldset>
          <fieldset>
            <legend>Difficulty</legend>
            <div className="choice-row">
              {DIFFICULTIES.map((d) => (
                <button
                  key={d}
                  type="button"
                  className={difficulty === d ? 'choice active' : 'choice'}
                  onClick={() => setDifficulty(d)}
                >
                  {d}
                </button>
              ))}
            </div>
          </fieldset>
          <p className="muted small">
            Starting: <strong>{interviewType}</strong> · <strong>{domain}</strong> ·{' '}
            <strong>{difficulty}</strong>
          </p>
          <button type="submit" className="primary" disabled={starting}>
            {starting ? 'Generating fresh questions…' : 'Begin interview'}
          </button>
        </form>
      </section>
    </>
  )
}
