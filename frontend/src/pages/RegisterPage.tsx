import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { AuthShell } from '../AuthShell'
import { PasswordStrengthMeter } from './LoginPage'

export function RegisterPage() {
  const { register, registerWithFirebase, loginWithGoogle, user, loading, firebaseEnabled } = useAuth()
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [useFirebase, setUseFirebase] = useState(firebaseEnabled)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  if (!loading && user) return <Navigate to="/app" replace />

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      if (useFirebase && firebaseEnabled) {
        await registerWithFirebase(fullName, email, password, true)
      } else {
        await register(fullName, email, password, true)
      }
      navigate('/app')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Registration failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell title="Create account" subtitle="Simulate. Evaluate. Improve — start in minutes.">
      <form
        className="auth-panel inline"
        onSubmit={onSubmit}
        style={{ display: 'grid', gap: '0.9rem' }}
      >
        {error && <p className="error">{error}</p>}
        <label>
          Full name
          <input value={fullName} onChange={(e) => setFullName(e.target.value)} required autoComplete="name" />
        </label>
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={6}
            autoComplete="new-password"
          />
        </label>
        <PasswordStrengthMeter password={password} />
        {firebaseEnabled && (
          <label className="checkbox-row">
            <input type="checkbox" checked={useFirebase} onChange={(e) => setUseFirebase(e.target.checked)} />
            Create account with Firebase
          </label>
        )}
        <button type="submit" className="primary" disabled={submitting}>
          {submitting ? 'Creating…' : 'Create account'}
        </button>
        {firebaseEnabled && (
          <>
            <div className="divider">or</div>
            <button
              type="button"
              disabled={submitting}
              onClick={async () => {
                setSubmitting(true)
                setError('')
                try {
                  await loginWithGoogle()
                  navigate('/app')
                } catch (err) {
                  setError(err instanceof Error ? err.message : 'Google sign-up failed')
                } finally {
                  setSubmitting(false)
                }
              }}
            >
              Sign up with Google
            </button>
          </>
        )}
        <p className="muted small">
          Already registered? <Link to="/login">Sign in</Link>
        </p>
      </form>
    </AuthShell>
  )
}
