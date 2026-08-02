import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  apiRequest,
  uploadMediaAnswer,
  type InterviewSession,
} from '../api'
import { useAuth } from '../auth'
import { isSpeechSupported, speakText, stopSpeaking } from '../speech'

export function InterviewPage() {
  const { id } = useParams<{ id: string }>()
  const { token } = useAuth()
  const navigate = useNavigate()
  const [session, setSession] = useState<InterviewSession | null>(null)
  const [index, setIndex] = useState(0)
  const [text, setText] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [recording, setRecording] = useState(false)
  const [mode, setMode] = useState<'Text' | 'Voice' | 'Video'>('Text')
  const [voiceOn, setVoiceOn] = useState(true)
  const [speaking, setSpeaking] = useState(false)
  const mediaRecorderRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<Blob[]>([])
  const previewRef = useRef<HTMLVideoElement | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const speechSupported = isSpeechSupported()

  useEffect(() => {
    if (!token || !id) return
    let cancelled = false
    async function load() {
      try {
        const data = await apiRequest<InterviewSession>(`/api/interviews/${id}`, { token })
        if (cancelled) return
        setSession(data)
        const firstUnanswered = data.questions.findIndex((q) => !q.answer)
        setIndex(firstUnanswered >= 0 ? firstUnanswered : 0)
        const current = data.questions[firstUnanswered >= 0 ? firstUnanswered : 0]
        setText(current?.answer?.textContent || current?.answer?.transcript || '')
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load interview')
      }
    }
    void load()
    return () => {
      cancelled = true
      stopStream()
      stopSpeaking()
    }
  }, [token, id])

  // Read each question aloud (and still show the text on screen)
  useEffect(() => {
    const questionText = session?.questions[index]?.text
    if (!questionText || !voiceOn || !speechSupported || recording) return

    let cancelled = false
    setSpeaking(true)
    void speakText(questionText)
      .catch(() => {
        /* ignore speech errors */
      })
      .finally(() => {
        if (!cancelled) setSpeaking(false)
      })

    return () => {
      cancelled = true
      stopSpeaking()
      setSpeaking(false)
    }
  }, [session, index, voiceOn, speechSupported, recording])

  function stopStream() {
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
    mediaRecorderRef.current = null
    setRecording(false)
  }

  async function replayQuestion() {
    const questionText = session?.questions[index]?.text
    if (!questionText || !speechSupported) return
    setSpeaking(true)
    try {
      await speakText(questionText)
    } catch {
      /* ignore */
    } finally {
      setSpeaking(false)
    }
  }

  async function saveTextAnswer() {
    if (!token || !session || !id) return
    const question = session.questions[index]
    if (!question) return
    setBusy(true)
    setError('')
    try {
      await apiRequest(`/api/interviews/${id}/answers`, {
        method: 'POST',
        token,
        body: JSON.stringify({
          questionId: question.id,
          textContent: text,
          inputType: 'Text',
        }),
      })
      const refreshed = await apiRequest<InterviewSession>(`/api/interviews/${id}`, { token })
      setSession(refreshed)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save answer')
    } finally {
      setBusy(false)
    }
  }

  async function startRecording() {
    setError('')
    // Stop AI voice so it isn't captured by the mic
    stopSpeaking()
    setSpeaking(false)
    try {
      const constraints =
        mode === 'Video'
          ? { audio: true, video: true }
          : { audio: true, video: false }
      const stream = await navigator.mediaDevices.getUserMedia(constraints)
      streamRef.current = stream
      if (mode === 'Video' && previewRef.current) {
        previewRef.current.srcObject = stream
        await previewRef.current.play()
      }
      chunksRef.current = []
      const mimeType = mode === 'Video' ? 'video/webm' : 'audio/webm'
      const recorder = new MediaRecorder(stream, MediaRecorder.isTypeSupported(mimeType) ? { mimeType } : undefined)
      mediaRecorderRef.current = recorder
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data)
      }
      recorder.onstop = () => {
        void submitRecording()
      }
      recorder.start()
      setRecording(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not access microphone/camera')
    }
  }

  function stopRecording() {
    mediaRecorderRef.current?.stop()
    setRecording(false)
  }

  async function submitRecording() {
    if (!token || !session || !id) return
    const question = session.questions[index]
    if (!question) return
    const blob = new Blob(chunksRef.current, {
      type: mode === 'Video' ? 'video/webm' : 'audio/webm',
    })
    stopStream()
    setBusy(true)
    setError('')
    try {
      await uploadMediaAnswer(
        token,
        id,
        question.id,
        mode === 'Video' ? 'Video' : 'Voice',
        blob,
        mode === 'Video' ? 'answer.webm' : 'answer-audio.webm',
        text || undefined,
      )
      const refreshed = await apiRequest<InterviewSession>(`/api/interviews/${id}`, { token })
      setSession(refreshed)
      const updated = refreshed.questions[index]
      if (updated?.answer?.transcript) {
        setText(updated.answer.transcript)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Media upload failed')
    } finally {
      setBusy(false)
    }
  }

  async function completeInterview() {
    if (!token || !id) return
    stopSpeaking()
    setBusy(true)
    setError('')
    try {
      if (mode === 'Text' && text.trim()) {
        await saveTextAnswer()
      }
      await apiRequest(`/api/interviews/${id}/complete`, { method: 'POST', token })
      navigate(`/app/results/${id}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not complete interview')
      setBusy(false)
    }
  }

  function goToQuestion(next: number) {
    stopSpeaking()
    setIndex(next)
    setText(
      session?.questions[next]?.answer?.textContent ||
        session?.questions[next]?.answer?.transcript ||
        '',
    )
  }

  if (!session) {
    return (
      <section className="panel">
        <p>{error || 'Loading interview…'}</p>
      </section>
    )
  }

  if (session.status === 'Completed') {
    return (
      <section className="panel">
        <h1>Interview already completed</h1>
        <Link className="primary" to={`/app/results/${session.id}`}>
          View results
        </Link>
      </section>
    )
  }

  const question = session.questions[index]
  const progress = `${index + 1} / ${session.questions.length}`
  const progressPct = ((index + 1) / session.questions.length) * 100

  return (
    <section className="panel interview-panel">
      <div className="interview-meta">
        <p className="eyebrow">
          {session.interviewType} · {session.domain} · {session.difficulty}
        </p>
        <p className="muted">Question {progress}</p>
      </div>
      <div className="progress-track" aria-hidden>
        <span style={{ width: `${progressPct}%` }} />
      </div>

      <div className="question-block">
        <div className="question-toolbar">
          <span className={`speak-status ${speaking ? 'active' : ''}`}>
            {speaking ? 'AI interviewer speaking…' : 'Question (text + voice)'}
          </span>
          {speechSupported && (
            <div className="speak-controls">
              <button
                type="button"
                className={voiceOn ? 'choice active' : 'choice'}
                onClick={() => {
                  if (voiceOn) stopSpeaking()
                  setVoiceOn((v) => !v)
                }}
                disabled={recording || busy}
              >
                {voiceOn ? 'Voice on' : 'Voice muted'}
              </button>
              <button
                type="button"
                className="choice"
                onClick={() => void replayQuestion()}
                disabled={recording || busy || speaking || !voiceOn}
              >
                Replay question
              </button>
            </div>
          )}
        </div>
        <h1 className="question-text">{question?.text}</h1>
        {!speechSupported && (
          <p className="muted small">Voice readout isn’t supported in this browser — text is still shown.</p>
        )}
      </div>

      {error && <p className="error">{error}</p>}

      <div className="choice-row multimodal-toggle">
        {(['Text', 'Voice', 'Video'] as const).map((m) => (
          <button
            key={m}
            type="button"
            className={mode === m ? 'choice active' : 'choice'}
            onClick={() => {
              stopStream()
              setMode(m)
            }}
            disabled={recording || busy}
          >
            {m === 'Text' ? 'Text answer' : m === 'Voice' ? 'Voice answer' : 'Video answer'}
          </button>
        ))}
      </div>
      <p className="muted small">
        The AI asks each question by voice and shows the text. Answer with text, microphone, or camera.
      </p>

      {mode === 'Text' && (
        <textarea
          rows={8}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Type your answer…"
        />
      )}

      {mode !== 'Text' && (
        <div className="media-box">
          {mode === 'Video' && <video ref={previewRef} muted playsInline className="preview" />}
          <div className="media-actions">
            {!recording ? (
              <button type="button" className="primary" onClick={() => void startRecording()} disabled={busy}>
                Start {mode.toLowerCase()} recording
              </button>
            ) : (
              <button type="button" className="danger" onClick={stopRecording}>
                Stop & upload
              </button>
            )}
          </div>
          <p className="muted small">
            Recording is transcribed with Groq Whisper, then included in personalized scoring.
          </p>
          <label>
            Optional notes / override transcript
            <textarea rows={4} value={text} onChange={(e) => setText(e.target.value)} />
          </label>
        </div>
      )}

      {question?.answer && (
        <p className="success">
          Saved via {question.answer.inputType}
          {question.answer.transcript ? ` · transcript ready` : ''}
        </p>
      )}

      <div className="action-row">
        <button
          type="button"
          disabled={index === 0 || busy || recording}
          onClick={() => goToQuestion(index - 1)}
        >
          Previous
        </button>
        {mode === 'Text' && (
          <button type="button" onClick={() => void saveTextAnswer()} disabled={busy || !text.trim()}>
            Save answer
          </button>
        )}
        {index < session.questions.length - 1 ? (
          <button
            type="button"
            className="primary"
            disabled={busy || recording}
            onClick={async () => {
              if (mode === 'Text' && text.trim()) await saveTextAnswer()
              goToQuestion(index + 1)
            }}
          >
            Next
          </button>
        ) : (
          <button type="button" className="primary" disabled={busy || recording} onClick={() => void completeInterview()}>
            {busy ? 'Evaluating…' : 'Finish & evaluate'}
          </button>
        )}
      </div>
    </section>
  )
}
