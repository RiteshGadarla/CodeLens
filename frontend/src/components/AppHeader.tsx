import { ChevronDown, House, LogOut } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '@/lib/auth'
import { cn } from '@/lib/cn'
import { Logo } from './Logo'

const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join('') || '?'

export function UserMenu({ placement = 'below', showName = false }: { placement?: 'below' | 'above'; showName?: boolean }) {
  const { user, signOut } = useAuth()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()

  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => !ref.current?.contains(e.target as Node) && setOpen(false)
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  if (!user) return null
  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        className="flex w-full items-center gap-2 rounded-lg p-1 pr-2 text-left transition hover:bg-slate-100"
      >
        <span className="inline-flex size-8 shrink-0 items-center justify-center rounded-full bg-indigo-100 text-xs font-semibold text-indigo-700">
          {initials(user.name)}
        </span>
        {showName && (
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-medium text-slate-800">{user.name}</span>
            <span className="block truncate text-[11px] text-slate-400">{user.email}</span>
          </span>
        )}
        <ChevronDown className="size-4 shrink-0 text-slate-400" />
      </button>
      {open && (
        <div
          role="menu"
          className={cn(
            'absolute z-40 w-60 rounded-xl border border-slate-200 bg-white p-1.5 shadow-lg',
            placement === 'below' ? 'top-full right-0 mt-2' : 'bottom-full left-0 mb-2',
          )}
        >
          <div className="border-b border-slate-100 px-3 py-2">
            <p className="truncate text-sm font-semibold text-slate-900">{user.name}</p>
            <p className="truncate text-xs text-slate-500">{user.email}</p>
          </div>
          <Link role="menuitem" to="/" className="mt-1 flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-slate-700 hover:bg-slate-50">
            <House className="size-4" /> Home page
          </Link>
          <button
            role="menuitem"
            type="button"
            onClick={() => {
              signOut()
              navigate('/')
            }}
            className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"
          >
            <LogOut className="size-4" /> Sign out
          </button>
        </div>
      )}
    </div>
  )
}

export function AppHeader({ actions }: { actions?: ReactNode }) {
  return (
    <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/90 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-7xl items-center gap-6 px-6">
        <Link to="/dashboard" className="flex items-center gap-2">
          <Logo size={26} />
          <span className="text-[15px] font-semibold tracking-tight text-slate-900">CodeLens</span>
        </Link>
        <nav className="hidden items-center gap-1 sm:flex">
          <NavLink
            to="/dashboard"
            className={({ isActive }) =>
              cn('rounded-md px-3 py-1.5 text-sm font-medium', isActive ? 'bg-slate-100 text-slate-900' : 'text-slate-500 hover:text-slate-900')
            }
          >
            Dashboard
          </NavLink>
        </nav>
        <div className="ml-auto flex items-center gap-3">
          {actions}
          <UserMenu />
        </div>
      </div>
    </header>
  )
}
