const API_BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:5126'

export type User = {
  id: string
  fullName: string
  email: string
  createdAt: string
  provider?: string
  avatarUrl?: string | null
  phoneNumber?: string | null
  emailVerified?: boolean
  firebaseUid?: string | null
}

export type Answer = {
  id: string
  inputType: string
  textContent?: string | null
  transcript?: string | null
  mediaPath?: string | null
  submittedAt: string
}

export type Question = {
  id: string
  orderIndex: number
  text: string
  answer?: Answer | null
}

export type QuestionReview = {
  questionIndex: number
  verdict: 'correct' | 'partial' | 'incorrect' | 'unanswered' | string
  score: number
  showCorrectAnswer: boolean
  correctAnswer: string
  explanation: string
}

export type ScoreReport = {
  id: string
  technicalScore: number
  communicationScore: number
  confidenceScore: number
  problemSolvingScore: number
  overallScore: number
  feedback: string
  strengths: string
  weaknesses: string
  improvements: string
  createdAt: string
  questionReviews?: QuestionReview[]
}

export type AiStatus = {
  configured: boolean
  ok: boolean
  provider: string
  model: string
  message: string
}

export type InterviewSession = {
  id: string
  interviewType: string
  domain: string
  difficulty: string
  status: string
  createdAt: string
  completedAt?: string | null
  questions: Question[]
  scoreReport?: ScoreReport | null
}

export type InterviewSummary = {
  id: string
  interviewType: string
  domain: string
  difficulty: string
  status: string
  createdAt: string
  completedAt?: string | null
  overallScore?: number | null
}

export type AnalyticsSummary = {
  totalSessions: number
  completedSessions: number
  averageOverallScore: number
  averageTechnical: number
  averageCommunication: number
  averageConfidence: number
  averageProblemSolving: number
  currentStreakDays: number
  weeklyGoalTarget: number
  weeklyCompleted: number
  recentSessions: InterviewSummary[]
  domainTrends: { domain: string; sessions: number; averageScore: number }[]
}

export type LearningRecommendations = {
  focusSkills: string[]
  resources: {
    title: string
    description: string
    url: string
    skill: string
    level: string
  }[]
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit & { token?: string | null; json?: boolean } = {},
): Promise<T> {
  const { token, headers, json = true, ...rest } = options
  const finalHeaders: Record<string, string> = {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
  if (json) finalHeaders['Content-Type'] = 'application/json'
  Object.assign(finalHeaders, headers || {})

  const res = await fetch(`${API_BASE}${path}`, {
    ...rest,
    headers: finalHeaders,
  })

  if (!res.ok) {
    let message = res.statusText
    try {
      const data = await res.json()
      message = data.message || data.title || message
    } catch {
      /* ignore */
    }
    throw new Error(message)
  }

  if (res.status === 204) return undefined as T
  const contentType = res.headers.get('content-type') || ''
  if (contentType.includes('text/html')) {
    return (await res.text()) as T
  }
  return res.json()
}

export async function uploadMediaAnswer(
  token: string,
  sessionId: string,
  questionId: string,
  inputType: 'Voice' | 'Video',
  file: Blob,
  fileName: string,
  textContent?: string,
): Promise<Answer> {
  const form = new FormData()
  form.append('questionId', questionId)
  form.append('inputType', inputType)
  form.append('file', file, fileName)
  if (textContent) form.append('textContent', textContent)

  const res = await fetch(`${API_BASE}/api/interviews/${sessionId}/answers/media`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  })

  if (!res.ok) {
    let message = res.statusText
    try {
      const data = await res.json()
      message = data.message || message
    } catch {
      /* ignore */
    }
    throw new Error(message)
  }

  return res.json()
}

export function reportUrl(sessionId: string): string {
  return `${API_BASE}/api/interviews/${sessionId}/report`
}

export { API_BASE }
