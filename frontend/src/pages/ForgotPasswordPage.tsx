import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { apiRequest } from '../api'
import { AuthShell } from '../AuthShell'
import { firebaseForgotPassword, isFirebaseConfigured } from '../firebase'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [useFirebase, setUseFirebase] = useState(isFirebaseConfigured())

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setMessage('')
    setSubmitting(true)
    try {
      if (useFirebase && isFirebaseConfigured()) {
        await firebaseForgotPassword(email)
        setMessage('Password reset email sent (check your inbox).')
      } else {
        const res = await apiRequest<{ message: string }>('/api/auth/forgot-password', {
          method: 'POST',
          body: JSON.stringify({ email }),
        })
        setMessage(res.message)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Request failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell title="Forgot password" subtitle="We’ll help you reset access to your account.">
      <form
        className="auth-panel inline"
        onSubmit={onSubmit}
        style={{ display: 'grid', gap: '0.9rem' }}
      >
        {error && <p className="error">{error}</p>}
        {message && <p className="success">{message}</p>}
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        {isFirebaseConfigured() && (
          <label className="checkbox-row">
            <input type="checkbox" checked={useFirebase} onChange={(e) => setUseFirebase(e.target.checked)} />
            Reset via Firebase email
          </label>
        )}
        <button type="submit" className="primary" disabled={submitting}>
          {submitting ? 'Sending…' : 'Send reset link'}
        </button>
        <p className="muted small">
          <Link to="/login">Back to sign in</Link>
        </p>
      </form>
    </AuthShell>
  )
}
