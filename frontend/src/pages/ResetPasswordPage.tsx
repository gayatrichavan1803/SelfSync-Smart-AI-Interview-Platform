import { useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { apiRequest } from '../api'
import { AuthShell } from '../AuthShell'
import { PasswordStrengthMeter } from './LoginPage'

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const tokenFromUrl = useMemo(() => params.get('token') ?? '', [params])
  const [token, setToken] = useState(tokenFromUrl)
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setMessage('')
    if (password !== confirm) {
      setError('Passwords do not match.')
      return
    }
    setSubmitting(true)
    try {
      const res = await apiRequest<{ message: string }>('/api/auth/reset-password', {
        method: 'POST',
        body: JSON.stringify({ token, password }),
      })
      setMessage(res.message)
      setTimeout(() => navigate('/login'), 1200)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Reset failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell title="Reset password" subtitle="Set a new password using your reset token.">
      <form
        className="auth-panel inline"
        onSubmit={onSubmit}
        style={{ display: 'grid', gap: '0.9rem' }}
      >
        {error && <p className="error">{error}</p>}
        {message && <p className="success">{message}</p>}
        <label>
          Reset token
          <input value={token} onChange={(e) => setToken(e.target.value)} required />
        </label>
        <label>
          New password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={6}
          />
        </label>
        <PasswordStrengthMeter password={password} />
        <label>
          Confirm password
          <input
            type="password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            required
            minLength={6}
          />
        </label>
        <button type="submit" className="primary" disabled={submitting}>
          {submitting ? 'Saving…' : 'Update password'}
        </button>
        <p className="muted small">
          <Link to="/login">Back to sign in</Link>
        </p>
      </form>
    </AuthShell>
  )
}
