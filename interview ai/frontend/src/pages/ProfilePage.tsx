import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth'

export function ProfilePage() {
  const { user, updateProfile } = useAuth()
  const [fullName, setFullName] = useState(user?.fullName ?? '')
  const [phoneNumber, setPhoneNumber] = useState(user?.phoneNumber ?? '')
  const [avatarUrl, setAvatarUrl] = useState(user?.avatarUrl ?? '')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    setFullName(user?.fullName ?? '')
    setPhoneNumber(user?.phoneNumber ?? '')
    setAvatarUrl(user?.avatarUrl ?? '')
  }, [user])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setSaving(true)
    setError('')
    setMessage('')
    try {
      await updateProfile(fullName, phoneNumber, avatarUrl)
      setMessage('Profile updated.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Update failed')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="panel">
      <header className="section-head">
        <p className="eyebrow">Account</p>
        <h1>Profile</h1>
        <p className="muted">
          {user?.email}
          {user?.provider ? ` · provider: ${user.provider}` : ''}
          {user?.firebaseUid ? ` · Firebase mapped` : ''}
        </p>
      </header>
      {user?.avatarUrl && (
        <img src={user.avatarUrl} alt="" className="avatar-preview" width={72} height={72} />
      )}
      {error && <p className="error">{error}</p>}
      {message && <p className="success">{message}</p>}
      <form className="auth-panel inline" onSubmit={onSubmit}>
        <label>
          Full name
          <input value={fullName} onChange={(e) => setFullName(e.target.value)} required />
        </label>
        <label>
          Phone
          <input value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} />
        </label>
        <label>
          Avatar URL
          <input value={avatarUrl} onChange={(e) => setAvatarUrl(e.target.value)} />
        </label>
        <button type="submit" className="primary" disabled={saving}>
          {saving ? 'Saving…' : 'Save changes'}
        </button>
      </form>
    </section>
  )
}
