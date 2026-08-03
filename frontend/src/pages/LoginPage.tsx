import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { passwordStrength, useAuth } from '../auth'
import { AuthShell } from '../AuthShell'

export function LoginPage() {
  const {
    login,
    loginWithFirebaseEmail,
    loginWithGoogle,
    user,
    loading,
    firebaseEnabled,
  } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(true)
  const [useFirebase, setUseFirebase] = useState(firebaseEnabled)
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  if (!loading && user) return <Navigate to="/app" replace />

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      if (useFirebase && firebaseEnabled) {
        await loginWithFirebaseEmail(email, password, rememberMe)
      } else {
        await login(email, password, rememberMe)
      }
      navigate('/app')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed')
    } finally {
      setSubmitting(false)
    }
  }

  async function onGoogle() {
    setError('')
    setSubmitting(true)
    try {
      await loginWithGoogle()
      navigate('/app')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Google sign-in failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell title="Welcome back" subtitle="Sign in to continue your interview practice.">
      <form
        className="auth-panel inline"
        onSubmit={onSubmit}
        style={{ display: 'grid', gap: '0.9rem' }}
      >
        {error && <p className="error">{error}</p>}
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
        </label>
        <label>
          Password
          <div className="password-row">
            <input
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={6}
              autoComplete="current-password"
            />
            <button type="button" className="choice" onClick={() => setShowPassword((v) => !v)}>
              {showPassword ? 'Hide' : 'Show'}
            </button>
          </div>
        </label>
        <label className="checkbox-row">
          <input type="checkbox" checked={rememberMe} onChange={(e) => setRememberMe(e.target.checked)} />
          Remember me
        </label>
        {firebaseEnabled && (
          <label className="checkbox-row">
            <input type="checkbox" checked={useFirebase} onChange={(e) => setUseFirebase(e.target.checked)} />
            Use Firebase auth
          </label>
        )}
        <button type="submit" className="primary" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
        {firebaseEnabled && (
          <>
            <div className="divider">or</div>
            <button type="button" onClick={() => void onGoogle()} disabled={submitting}>
              Continue with Google
            </button>
          </>
        )}
        <p className="muted small">
          <Link to="/forgot-password">Forgot password?</Link>
        </p>
        <p className="muted small">
          New here? <Link to="/register">Create an account</Link>
        </p>
      </form>
    </AuthShell>
  )
}

export function PasswordStrengthMeter({ password }: { password: string }) {
  const { checks, label, score } = passwordStrength(password)
  if (!password) return null
  return (
    <div className="strength-box">
      <div className="strength-bar">
        <span style={{ width: `${(score / 5) * 100}%` }} data-level={label.toLowerCase()} />
      </div>
      <p className="muted small">Strength: {label}</p>
      <ul className="strength-list">
        <li className={checks.length ? 'ok' : ''}>8+ characters</li>
        <li className={checks.upper ? 'ok' : ''}>Uppercase</li>
        <li className={checks.lower ? 'ok' : ''}>Lowercase</li>
        <li className={checks.number ? 'ok' : ''}>Number</li>
        <li className={checks.special ? 'ok' : ''}>Special character</li>
      </ul>
    </div>
  )
}
