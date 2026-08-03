import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { apiRequest, type User } from './api'
import {
  firebaseGoogleLogin,
  firebaseIdToken,
  firebaseLogin,
  firebaseRegister,
  isFirebaseConfigured,
} from './firebase'

type AuthContextValue = {
  user: User | null
  token: string | null
  loading: boolean
  firebaseEnabled: boolean
  login: (email: string, password: string, rememberMe?: boolean) => Promise<void>
  register: (fullName: string, email: string, password: string, rememberMe?: boolean) => Promise<void>
  loginWithGoogle: () => Promise<void>
  loginWithFirebaseEmail: (email: string, password: string, rememberMe?: boolean) => Promise<void>
  registerWithFirebase: (fullName: string, email: string, password: string, rememberMe?: boolean) => Promise<void>
  logout: () => void
  updateProfile: (fullName: string, phoneNumber?: string, avatarUrl?: string) => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)
const TOKEN_KEY = 'selfsync_token'
const REMEMBER_KEY = 'selfsync_remember'

function storeToken(token: string, rememberMe: boolean) {
  localStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(TOKEN_KEY)
  if (rememberMe) {
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(REMEMBER_KEY, '1')
  } else {
    sessionStorage.setItem(TOKEN_KEY, token)
    localStorage.removeItem(REMEMBER_KEY)
  }
}

function readToken() {
  return localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY)
}

function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REMEMBER_KEY)
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => readToken())
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const firebaseEnabled = isFirebaseConfigured()

  useEffect(() => {
    let cancelled = false
    async function load() {
      if (!token) {
        setUser(null)
        setLoading(false)
        return
      }
      try {
        const me = await apiRequest<User>('/api/auth/me', { token })
        if (!cancelled) setUser(me)
      } catch {
        clearToken()
        if (!cancelled) {
          setToken(null)
          setUser(null)
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [token])

  const applyAuth = useCallback((res: { token: string; user: User }, rememberMe = true) => {
    storeToken(res.token, rememberMe)
    setToken(res.token)
    setUser(res.user)
  }, [])

  const exchangeFirebase = useCallback(
    async (fbUser: import('firebase/auth').User, rememberMe = true) => {
      const idToken = await firebaseIdToken(fbUser)
      const res = await apiRequest<{ token: string; user: User }>('/api/auth/firebase', {
        method: 'POST',
        body: JSON.stringify({ idToken }),
      })
      applyAuth(res, rememberMe)
    },
    [applyAuth],
  )

  const login = useCallback(
    async (email: string, password: string, rememberMe = true) => {
      const res = await apiRequest<{ token: string; user: User }>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      applyAuth(res, rememberMe)
    },
    [applyAuth],
  )

  const register = useCallback(
    async (fullName: string, email: string, password: string, rememberMe = true) => {
      const res = await apiRequest<{ token: string; user: User }>('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify({ fullName, email, password }),
      })
      applyAuth(res, rememberMe)
    },
    [applyAuth],
  )

  const loginWithGoogle = useCallback(async () => {
    const fbUser = await firebaseGoogleLogin()
    await exchangeFirebase(fbUser, true)
  }, [exchangeFirebase])

  const loginWithFirebaseEmail = useCallback(
    async (email: string, password: string, rememberMe = true) => {
      const fbUser = await firebaseLogin(email, password)
      await exchangeFirebase(fbUser, rememberMe)
    },
    [exchangeFirebase],
  )

  const registerWithFirebase = useCallback(
    async (fullName: string, email: string, password: string, rememberMe = true) => {
      const fbUser = await firebaseRegister(email, password, fullName)
      await exchangeFirebase(fbUser, rememberMe)
    },
    [exchangeFirebase],
  )

  const logout = useCallback(() => {
    clearToken()
    setToken(null)
    setUser(null)
  }, [])

  const updateProfile = useCallback(
    async (fullName: string, phoneNumber?: string, avatarUrl?: string) => {
      if (!token) return
      const updated = await apiRequest<User>('/api/auth/profile', {
        method: 'PUT',
        token,
        body: JSON.stringify({ fullName, phoneNumber: phoneNumber ?? null, avatarUrl: avatarUrl ?? null }),
      })
      setUser(updated)
    },
    [token],
  )

  const value = useMemo(
    () => ({
      user,
      token,
      loading,
      firebaseEnabled,
      login,
      register,
      loginWithGoogle,
      loginWithFirebaseEmail,
      registerWithFirebase,
      logout,
      updateProfile,
    }),
    [
      user,
      token,
      loading,
      firebaseEnabled,
      login,
      register,
      loginWithGoogle,
      loginWithFirebaseEmail,
      registerWithFirebase,
      logout,
      updateProfile,
    ],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}

export function passwordStrength(password: string) {
  const checks = {
    length: password.length >= 8,
    upper: /[A-Z]/.test(password),
    lower: /[a-z]/.test(password),
    number: /\d/.test(password),
    special: /[^A-Za-z0-9]/.test(password),
  }
  const score = Object.values(checks).filter(Boolean).length
  const label = score <= 2 ? 'Weak' : score <= 4 ? 'Medium' : 'Strong'
  return { checks, score, label }
}
