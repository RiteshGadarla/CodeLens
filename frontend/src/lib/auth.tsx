import { useQuery, useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { api, tokenStore } from '@/api/client'
import { Button, ErrorState, Spinner } from '@/components/ui'
import type { AuthResponse, User } from '@/types/api'

interface AuthState {
  user: User | null
  signedIn: boolean
  error: unknown
  signIn: (email: string, password: string) => Promise<void>
  signUp: (name: string, email: string, password: string) => Promise<void>
  signOut: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient()
  const [token, setToken] = useState(tokenStore.get())
  const me = useQuery({
    queryKey: ['me', token],
    queryFn: () => api<User>('/auth/me'),
    enabled: token != null,
    staleTime: Infinity,
  })

  const signOut = useCallback(() => {
    tokenStore.set(null)
    setToken(null)
    qc.clear()
  }, [qc])

  useEffect(() => {
    tokenStore.onUnauthorized(signOut)
    return () => tokenStore.onUnauthorized(null)
  }, [signOut])

  const accept = useCallback(
    (res: AuthResponse) => {
      qc.clear()
      tokenStore.set(res.token)
      qc.setQueryData(['me', res.token], res.user)
      setToken(res.token)
    },
    [qc],
  )

  const value = useMemo<AuthState>(
    () => ({
      user: me.data ?? null,
      signedIn: token != null,
      error: me.error,
      signIn: async (email, password) =>
        accept(await api<AuthResponse>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) })),
      signUp: async (name, email, password) =>
        accept(
          await api<AuthResponse>('/auth/register', { method: 'POST', body: JSON.stringify({ name, email, password }) }),
        ),
      signOut,
    }),
    [me.data, me.error, token, accept, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth outside AuthProvider')
  return ctx
}

// route guard: waits for /auth/me before rendering private pages
export function RequireAuth() {
  const { signedIn, user, error, signOut } = useAuth()
  const location = useLocation()

  if (!signedIn) return <Navigate to="/signin" replace state={{ from: location.pathname + location.search }} />
  if (user) return <Outlet />
  if (error) {
    return (
      <div className="mx-auto max-w-md space-y-3 p-10">
        <ErrorState error={error} />
        <Button variant="secondary" onClick={signOut}>
          Sign in again
        </Button>
      </div>
    )
  }
  return <Spinner label="Restoring your session…" />
}

// sign-in pages send signed-in users on to where they were going
export function GuestOnly({ children }: { children: ReactNode }) {
  const { signedIn } = useAuth()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  return signedIn ? <Navigate to={from ?? '/dashboard'} replace /> : children
}
