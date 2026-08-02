import { Navigate, Outlet, NavLink } from 'react-router-dom'
import { useAuth } from './auth'

export function ProtectedLayout() {
  const { user, loading, logout } = useAuth()

  if (loading) {
    return (
      <div className="app-shell">
        <p className="loading">Loading SelfSync…</p>
      </div>
    )
  }

  if (!user) return <Navigate to="/login" replace />

  return (
    <div className="app-shell">
      <header className="topbar">
        <NavLink to="/app" className="brand-mark">
          SelfSync
        </NavLink>
        <nav aria-label="Main">
          <NavLink to="/app" end>
            Interview
          </NavLink>
          <NavLink to="/app/analytics">Analytics</NavLink>
          <NavLink to="/app/learning">Learning</NavLink>
          <NavLink to="/app/profile">Profile</NavLink>
          <button type="button" className="linkish" onClick={logout}>
            Sign out
          </button>
        </nav>
      </header>
      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}
