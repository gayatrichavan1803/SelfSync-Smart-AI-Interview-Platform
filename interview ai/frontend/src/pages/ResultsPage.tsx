import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { apiRequest, reportUrl, type InterviewSession, type QuestionReview } from '../api'
import { useAuth } from '../auth'

function reviewFor(reviews: QuestionReview[] | undefined, orderIndex: number) {
  return reviews?.find((r) => r.questionIndex === orderIndex + 1)
}

export function ResultsPage() {
  const { id } = useParams<{ id: string }>()
  const { token } = useAuth()
  const [session, setSession] = useState<InterviewSession | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!token || !id) return
    void apiRequest<InterviewSession>(`/api/interviews/${id}`, { token })
      .then(setSession)
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load results'))
  }, [token, id])

  if (error) return <section className="panel"><p className="error">{error}</p></section>
  if (!session) return <section className="panel"><p>Loading results…</p></section>

  const report = session.scoreReport
  if (!report) {
    return (
      <section className="panel">
        <h1>No score yet</h1>
        <Link to={`/app/interview/${session.id}`}>Continue interview</Link>
      </section>
    )
  }

  async function openReport() {
    if (!token || !id) return
    const html = await apiRequest<string>(`/api/interviews/${id}/report`, { token })
    const win = window.open('', '_blank')
    if (win) {
      win.document.write(html)
      win.document.close()
    } else {
      window.location.href = reportUrl(id)
    }
  }

  const scores = [
    { label: 'Overall', value: report.overallScore },
    { label: 'Technical', value: report.technicalScore },
    { label: 'Communication', value: report.communicationScore },
    { label: 'Confidence', value: report.confidenceScore },
    { label: 'Problem solving', value: report.problemSolvingScore },
  ]

  const reviews = report.questionReviews ?? []

  return (
    <section className="panel results-panel">
      <header className="section-head">
        <p className="eyebrow">
          {session.interviewType} · {session.domain} · {session.difficulty}
        </p>
        <h1>Interview results</h1>
        <p className="muted">Scores, coaching notes, and correct answers for missed questions.</p>
      </header>

      <div className="score-grid">
        {scores.map((s) => (
          <div key={s.label} className="score-tile">
            <span>{s.label}</span>
            <strong>{s.value.toFixed(1)}</strong>
          </div>
        ))}
      </div>

      <div className="feedback-block">
        <h2>Personalized feedback</h2>
        <p>{report.feedback}</p>
        <h3>Strengths</h3>
        <p>{report.strengths}</p>
        <h3>Weaknesses</h3>
        <p>{report.weaknesses}</p>
        <h3>Improvements</h3>
        <p>{report.improvements}</p>
      </div>

      <div className="feedback-block">
        <h2>Answer review</h2>
        <ul className="history-list answer-review-list">
          {session.questions.map((q) => {
            const review = reviewFor(reviews, q.orderIndex)
            const verdict = review?.verdict ?? 'ungraded'
            return (
              <li key={q.id} className={`answer-review verdict-${verdict}`}>
                <div>
                  <div className="answer-review-head">
                    <strong>
                      Q{q.orderIndex + 1}. {q.text}
                    </strong>
                    {review && (
                      <span className={`verdict-badge verdict-${verdict}`}>
                        {verdict} · {review.score.toFixed(0)}/100
                      </span>
                    )}
                  </div>
                  <p className="muted small">
                    Mode: {q.answer?.inputType ?? 'Unanswered'}
                    {q.answer?.transcript ? ' · transcribed' : ''}
                  </p>
                  <p>
                    <span className="review-label">Your answer</span>
                    {q.answer?.textContent || q.answer?.transcript || 'No answer recorded.'}
                  </p>
                  {review?.explanation && (
                    <p className="muted">
                      <span className="review-label">Why</span>
                      {review.explanation}
                    </p>
                  )}
                  {review?.showCorrectAnswer && review.correctAnswer && (
                    <div className="correct-answer-box">
                      <span className="review-label">Correct / model answer</span>
                      <p>{review.correctAnswer}</p>
                    </div>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      </div>

      <div className="action-row">
        <button type="button" className="primary" onClick={() => void openReport()}>
          Download HTML report
        </button>
        <Link to="/app">New interview</Link>
        <Link to="/app/analytics">Analytics</Link>
      </div>
    </section>
  )
}
