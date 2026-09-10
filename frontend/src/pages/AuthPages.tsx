import { ArrowRight, Crosshair, Eye, EyeOff, Gauge } from 'lucide-react'
import { useState, type FormEvent, type InputHTMLAttributes, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { ApiError } from '@/api/client'
import { Logo } from '@/components/Logo'
import { LyraIcon } from '@/components/Lyra'
import { Button, ErrorState } from '@/components/ui'
import { useAuth } from '@/lib/auth'

const inputClass =
  'w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-900 outline-none placeholder:text-slate-400 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100'

const highlights = [
  { icon: Crosshair, title: 'See the blast radius', body: 'Every dependent, endpoint and test a change reaches.' },
  { icon: Gauge, title: 'Explainable risk', body: 'Scores built from five open, measurable factors.' },
  { icon: LyraIcon, title: 'Ask Lyra', body: 'Answers grounded in your dependency graph, with citations.' },
]

function BrandPanel() {
  return (
    <div className="relative hidden overflow-hidden bg-slate-950 lg:flex">
      <div aria-hidden className="absolute inset-0 bg-[radial-gradient(70%_60%_at_80%_10%,rgba(99,102,241,0.45),transparent),radial-gradient(60%_50%_at_10%_90%,rgba(139,92,246,0.35),transparent)]" />
      <div
        aria-hidden
        className="absolute inset-0 [mask-image:radial-gradient(ellipse_at_center,black,transparent_75%)] opacity-20 [background-image:linear-gradient(to_right,#334155_1px,transparent_1px),linear-gradient(to_bottom,#334155_1px,transparent_1px)] [background-size:40px_40px]"
      />
      <div className="relative m-auto max-w-md px-10">
        <h2 className="text-3xl font-semibold tracking-tight text-balance text-white">Every change has a blast radius. See it first.</h2>
        <p className="mt-4 text-slate-300">CodeLens maps your Java code into a dependency graph so reviews start with facts, not hunches.</p>
        <ul className="mt-10 space-y-6">
          {highlights.map((h) => (
            <li key={h.title} className="flex gap-4">
              <span className="inline-flex size-10 shrink-0 items-center justify-center rounded-xl bg-white/10 text-indigo-200 ring-1 ring-white/15">
                <h.icon className="size-5" />
              </span>
              <div>
                <p className="font-medium text-white">{h.title}</p>
                <p className="mt-0.5 text-sm text-slate-400">{h.body}</p>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}

function AuthShell({ title, subtitle, children, footer }: { title: string; subtitle: string; children: ReactNode; footer: ReactNode }) {
  return (
    <div className="grid min-h-full bg-white lg:grid-cols-[1fr_1.1fr]">
      <div className="flex flex-col px-6 py-8 sm:px-12">
        <Link to="/" className="flex w-fit items-center gap-2">
          <Logo size={28} />
          <span className="text-[15px] font-semibold tracking-tight text-slate-900">CodeLens</span>
        </Link>
        <div className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center py-12">
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
          <p className="mt-1.5 text-sm text-slate-500">{subtitle}</p>
          <div className="mt-8">{children}</div>
          <p className="mt-8 text-center text-sm text-slate-500">{footer}</p>
        </div>
      </div>
      <BrandPanel />
    </div>
  )
}

function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-medium text-slate-700">{label}</span>
      {children}
      {hint && <span className="mt-1.5 block text-xs text-slate-400">{hint}</span>}
    </label>
  )
}

function PasswordInput(props: InputHTMLAttributes<HTMLInputElement>) {
  const [visible, setVisible] = useState(false)
  return (
    <div className="relative">
      <input {...props} type={visible ? 'text' : 'password'} className={`${inputClass} pr-10`} />
      <button
        type="button"
        onClick={() => setVisible((v) => !v)}
        aria-label={visible ? 'Hide password' : 'Show password'}
        className="absolute inset-y-0 right-0 flex items-center px-3 text-slate-400 hover:text-slate-600"
      >
        {visible ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
      </button>
    </div>
  )
}

function useSubmit(action: () => Promise<void>) {
  const [error, setError] = useState<unknown>(null)
  const [pending, setPending] = useState(false)
  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setPending(true)
    setError(null)
    try {
      await action()
    } catch (err) {
      setError(err)
      setPending(false)
    }
  }
  return { submit, error, pending }
}

// keep the intended destination when switching between the two pages
function useFromState() {
  return useLocation().state as { from?: string } | null
}

export function SignInPage() {
  const { signIn } = useAuth()
  const from = useFromState()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const { submit, error, pending } = useSubmit(() => signIn(email, password))

  return (
    <AuthShell
      title="Welcome back"
      subtitle="Sign in to your CodeLens workspace."
      footer={
        <>
          New to CodeLens?{' '}
          <Link to="/signup" state={from} className="font-medium text-indigo-600 hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-5">
        <Field label="Email">
          <input type="email" required autoComplete="email" autoFocus value={email} onChange={(e) => setEmail(e.target.value)} className={inputClass} placeholder="you@company.com" />
        </Field>
        <Field label="Password">
          <PasswordInput required autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        {error != null && <ErrorState error={error instanceof ApiError && error.status === 401 ? new Error('Incorrect email or password.') : error} />}
        <Button type="submit" loading={pending} className="w-full py-2.5">
          Sign in <ArrowRight className="size-4" />
        </Button>
      </form>
    </AuthShell>
  )
}

export function SignUpPage() {
  const { signUp } = useAuth()
  const from = useFromState()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const { submit, error, pending } = useSubmit(() => signUp(name, email, password))

  return (
    <AuthShell
      title="Create your account"
      subtitle="Analyze your first repository in a couple of minutes."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/signin" state={from} className="font-medium text-indigo-600 hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-5">
        <Field label="Name">
          <input required maxLength={100} autoComplete="name" autoFocus value={name} onChange={(e) => setName(e.target.value)} className={inputClass} placeholder="Ada Lovelace" />
        </Field>
        <Field label="Work email">
          <input type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} className={inputClass} placeholder="you@company.com" />
        </Field>
        <Field label="Password" hint="At least 8 characters.">
          <PasswordInput required minLength={8} maxLength={72} autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        {error != null && <ErrorState error={error} />}
        <Button type="submit" loading={pending} className="w-full py-2.5">
          Create account <ArrowRight className="size-4" />
        </Button>
      </form>
    </AuthShell>
  )
}
