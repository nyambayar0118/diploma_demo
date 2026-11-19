import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, getToken, setToken, UNAUTHORIZED_EVENT, type AuthResponse, type User } from './api'

type AuthState = {
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<User>
  register: (data: { email: string; password: string; displayName: string; username: string }) => Promise<User>
  loginWithGoogle: (credential: string) => Promise<User>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(() => getToken() !== null)

  const logout = useCallback(() => {
    setToken(null)
    setUser(null)
  }, [])

  // Restore the session from the saved token
  useEffect(() => {
    if (!getToken()) return
    api<User>('/api/users/me')
      .then(setUser)
      .catch(() => logout())
      .finally(() => setLoading(false))
  }, [logout])

  useEffect(() => {
    window.addEventListener(UNAUTHORIZED_EVENT, logout)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, logout)
  }, [logout])

  const accept = (res: AuthResponse) => {
    setToken(res.token)
    setUser(res.user)
    return res.user
  }

  const login = (email: string, password: string) =>
    api<AuthResponse>('/api/auth/login', { method: 'POST', body: { email, password } }).then(accept)

  const register = (data: { email: string; password: string; displayName: string; username: string }) =>
    api<AuthResponse>('/api/auth/register', { method: 'POST', body: data }).then(accept)

  const loginWithGoogle = (credential: string) =>
    api<AuthResponse>('/api/auth/oauth', { method: 'POST', body: { provider: 'GOOGLE', token: credential } }).then(accept)

  return (
    <AuthContext.Provider value={{ user, loading, login, register, loginWithGoogle, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
