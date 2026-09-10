import {
  ArrowRight, Check, Crosshair, FlaskConical, Gauge, GitBranch, Layers, Network, OctagonAlert, RefreshCw,
  ShieldCheck, TriangleAlert,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { Logo } from '@/components/Logo'
import { LyraIcon, LyraMark } from '@/components/Lyra'
import { useAuth } from '@/lib/auth'
import { factorLabel, factorWeight, riskStyle } from '@/lib/risk'

const primaryBtn =
  'inline-flex items-center justify-center gap-2 rounded-lg bg-indigo-500 px-4 py-2.5 text-sm font-semibold text-white shadow-lg shadow-indigo-500/25 transition hover:bg-indigo-400'
const ghostBtn =
  'inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold text-slate-200 ring-1 ring-white/15 ring-inset transition hover:bg-white/5'

const navLinks = [
  { label: 'Features', href: '#features' },
  { label: 'How it works', href: '#how' },
  { label: 'Lyra', href: '#lyra' },
  { label: 'Risk scoring', href: '#scoring' },
]

// numbers measured on real repositories during development
const proof = [
  { value: '~1 s', label: 'to turn 145 Java files into 1,967 entities and 2,739 dependencies' },
  { value: '9', label: 'relationship types, from method calls and overrides to HTTP routes' },
  { value: '0', label: 'embedding API calls: code vectors are computed locally' },
  { value: 'SHA-256', label: 'file diffing, so incremental runs re-parse only what changed' },
]

const features = [
  {
    icon: Network,
    title: 'Dependency graph',
    body: 'A two-pass JavaParser analysis resolves calls, overrides, type usage and Spring routes into one navigable graph of classes, methods and endpoints.',
  },
  {
    icon: Crosshair,
    title: 'Change impact',
    body: 'Pick any class or method to see every upstream dependent, the endpoints and tests it reaches, and the exact path that connects them.',
  },
  {
    icon: Gauge,
    title: 'Explainable risk score',
    body: 'Every component gets a 0–100 score from dependents, depth, coupling, complexity and API exposure. Each point traces back to a factor.',
  },
  {
    icon: RefreshCw,
    title: 'Incremental analysis',
    body: 'Files are diffed by hash. Only what changed, plus the files that depend on it, is parsed and resolved again.',
  },
  {
    icon: Layers,
    title: 'Architecture metrics',
    body: 'Afferent and efferent coupling, instability, abstractness and distance from the main sequence for every package and module.',
  },
  {
    icon: LyraIcon,
    title: 'Lyra assistant',
    body: 'Ask about the codebase in plain English. Lyra answers from graph facts and retrieved source, and cites every excerpt it uses.',
  },
]

const steps = [
  { icon: GitBranch, title: 'Connect a repository', body: 'Paste a Git URL or upload a zip. CodeLens makes a shallow clone and finds the Java sources.' },
  { icon: Network, title: 'Build the graph', body: 'Sources are parsed in parallel, resolved into a dependency graph, scored and cached. Re-runs are incremental.' },
  { icon: ShieldCheck, title: 'Change with confidence', body: 'Review hotspots, trace impact before you merge and ask Lyra for a safe, ordered change plan.' },
]

const lyraPoints = [
  { title: 'Grounded, not guessed', body: 'Answers start from deterministic graph facts: dependents, affected endpoints and tests.' },
  { title: 'Cites its sources', body: 'Retrieved methods and types are numbered, and every citation links back to the code.' },
  { title: 'Honest about gaps', body: 'When the context is not enough, Lyra says what is missing instead of inventing it.' },
  { title: 'Light on quota', body: 'Embeddings run locally; answers are rate-limited and cached, so free tiers go a long way.' },
]

const stack = ['Java 21', 'Spring Boot 3', 'JavaParser', 'PostgreSQL', 'Redis', 'FastAPI', 'React 19', 'React Flow', 'Docker']

function Hero() {
  const { signedIn } = useAuth()
  return (
    <section className="relative overflow-hidden bg-slate-950 text-white">
      <div aria-hidden className="pointer-events-none absolute inset-0 bg-[radial-gradient(60%_50%_at_50%_0%,rgba(99,102,241,0.35),transparent)]" />
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 [mask-image:radial-gradient(ellipse_at_top,black,transparent_70%)] opacity-20 [background-image:linear-gradient(to_right,#334155_1px,transparent_1px),linear-gradient(to_bottom,#334155_1px,transparent_1px)] [background-size:44px_44px]"
      />
      <div className="relative mx-auto max-w-6xl px-6 pt-12 pb-24 text-center sm:pt-16">
        <Link to="/" className="mx-auto mb-12 flex w-fit items-center gap-2.5">
          <Logo size={36} />
          <span className="text-xl font-semibold tracking-tight text-white">CodeLens</span>
        </Link>
        <a href="#lyra" className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/5 px-3 py-1 text-xs text-slate-300 transition hover:bg-white/10">
          <LyraMark size={20} className="ring-white/20" /> Meet Lyra, the assistant that reads your dependency graph
          <ArrowRight className="size-3.5" />
        </a>
        <h1 className="mx-auto mt-7 max-w-4xl text-4xl font-semibold tracking-tight text-balance sm:text-6xl">
          Know what breaks{' '}
          <span className="bg-gradient-to-r from-indigo-300 via-violet-300 to-sky-300 bg-clip-text text-transparent">before</span> you
          change it.
        </h1>
        <p className="mx-auto mt-6 max-w-2xl text-lg text-pretty text-slate-300">
          CodeLens turns a Java repository into a living dependency graph, scores the change risk of every class and method, and shows
          the exact endpoints, modules and tests a change will touch.
        </p>
        <div className="mt-9 flex flex-wrap items-center justify-center gap-3">
          {signedIn ? (
            <Link to="/dashboard" className={primaryBtn}>
              Open your dashboard <ArrowRight className="size-4" />
            </Link>
          ) : (
            <>
              <Link to="/signup" className={primaryBtn}>
                Start analyzing free <ArrowRight className="size-4" />
              </Link>
              <Link to="/signin" className={ghostBtn}>
                Sign in
              </Link>
            </>
          )}
        </div>
        <ul className="mt-6 flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-sm text-slate-400">
          {['Git URL or zip upload', 'Nothing to install in your repo', 'Incremental re-analysis'].map((t) => (
            <li key={t} className="flex items-center gap-1.5">
              <Check className="size-4 text-indigo-300" /> {t}
            </li>
          ))}
        </ul>
        <ProductPreview />
      </div>
    </section>
  )
}

function ProductPreview() {
  const tiles = [
    { label: 'Entities', value: '335' },
    { label: 'Dependencies', value: '512' },
    { label: 'API endpoints', value: '17' },
    { label: 'Analyzed in', value: '2.7 s' },
  ]
  return (
    <div className="relative mx-auto mt-16 max-w-5xl">
      <div aria-hidden className="absolute -inset-6 rounded-[2rem] bg-gradient-to-b from-indigo-500/25 to-transparent blur-2xl" />
      <div className="relative overflow-hidden rounded-2xl border border-white/10 bg-slate-900/90 text-left shadow-2xl">
        <div className="flex items-center gap-2 border-b border-white/10 px-4 py-3">
          <span className="size-2.5 rounded-full bg-white/15" />
          <span className="size-2.5 rounded-full bg-white/15" />
          <span className="size-2.5 rounded-full bg-white/15" />
          <span className="ml-3 truncate rounded-md bg-white/5 px-3 py-1 font-mono text-[11px] text-slate-400">
            spring-petclinic / impact / OwnerRepository
          </span>
        </div>
        <div className="grid gap-5 p-5 sm:p-6 lg:grid-cols-[0.9fr_1.4fr]">
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              {tiles.map((t) => (
                <div key={t.label} className="rounded-xl border border-white/10 bg-white/[0.03] p-3">
                  <p className="text-[11px] text-slate-400">{t.label}</p>
                  <p className="mt-1 text-xl font-semibold text-white tabular-nums">{t.value}</p>
                </div>
              ))}
            </div>
            <div className="rounded-xl border border-white/10 bg-white/[0.03] p-4">
              <p className="text-xs font-medium text-slate-300">Changing OwnerRepository reaches</p>
              <ul className="mt-3 space-y-2 text-sm">
                <li className="flex items-center justify-between text-slate-300">
                  <span className="flex items-center gap-2"><Network className="size-4 text-slate-500" /> Controllers</span>
                  <span className="font-semibold text-white tabular-nums">3</span>
                </li>
                <li className="flex items-center justify-between text-slate-300">
                  <span className="flex items-center gap-2"><Crosshair className="size-4 text-slate-500" /> Endpoints</span>
                  <span className="font-semibold text-white tabular-nums">4</span>
                </li>
                <li className="flex items-center justify-between text-slate-300">
                  <span className="flex items-center gap-2"><FlaskConical className="size-4 text-slate-500" /> Tests to run</span>
                  <span className="font-semibold text-white tabular-nums">5</span>
                </li>
              </ul>
              <p className="mt-4 flex items-center gap-1.5 text-xs text-slate-300">
                <OctagonAlert className="size-4" style={{ color: riskStyle.HIGH.hex }} /> High change risk
              </p>
            </div>
          </div>
          <ImpactIllustration />
        </div>
      </div>
    </div>
  )
}

// hand-placed graph: endpoints -> controllers -> repository
function ImpactIllustration() {
  const endpoints = [
    { y: 44, label: 'GET /owners/{ownerId}', to: 0 },
    { y: 104, label: 'POST /owners/new', to: 0 },
    { y: 196, label: 'POST …/pets/new', to: 1 },
    { y: 256, label: 'POST …/visits/new', to: 2 },
  ]
  const controllers = [
    { y: 74, label: 'OwnerController' },
    { y: 150, label: 'PetController' },
    { y: 226, label: 'VisitController' },
  ]
  const target = { x: 470, y: 150 }
  const curve = (x1: number, y1: number, x2: number, y2: number) => `M${x1},${y1} C${x1 + 40},${y1} ${x2 - 40},${y2} ${x2},${y2}`

  return (
    <div className="rounded-xl border border-white/10 bg-slate-950/60 p-3">
      <svg viewBox="0 0 560 300" className="w-full" role="img" aria-label="Endpoints and controllers that depend on OwnerRepository">
        {endpoints.map((e) => (
          <path key={e.label} d={curve(180, e.y, 215, controllers[e.to].y)} fill="none" stroke="#475569" strokeWidth={1.5} />
        ))}
        {controllers.map((c) => (
          <path
            key={c.label}
            d={curve(355, c.y, target.x - 75, target.y)}
            fill="none"
            stroke="#818cf8"
            strokeWidth={1.5}
            strokeDasharray="5 7"
            className="motion-safe:animate-flow"
          />
        ))}
        {endpoints.map((e) => (
          <g key={e.label}>
            <rect x={10} y={e.y - 15} width={170} height={30} rx={8} fill="#0f172a" stroke="#334155" />
            <text x={22} y={e.y + 4} fontSize={11} fill="#cbd5e1" fontFamily="JetBrains Mono, monospace">
              {e.label}
            </text>
          </g>
        ))}
        {controllers.map((c) => (
          <g key={c.label}>
            <rect x={215} y={c.y - 17} width={140} height={34} rx={8} fill="#0f172a" stroke="#475569" />
            <circle cx={231} cy={c.y} r={4} fill={riskStyle.MEDIUM.hex} />
            <text x={242} y={c.y + 4} fontSize={11.5} fill="#e2e8f0" fontFamily="JetBrains Mono, monospace">
              {c.label}
            </text>
          </g>
        ))}
        <g>
          <rect x={target.x - 75} y={target.y - 22} width={150} height={44} rx={10} fill="#1e1b4b" stroke="#818cf8" strokeWidth={1.5} />
          <circle cx={target.x - 58} cy={target.y} r={4.5} fill={riskStyle.HIGH.hex} />
          <text x={target.x - 46} y={target.y + 4} fontSize={12} fontWeight={600} fill="#fff" fontFamily="JetBrains Mono, monospace">
            OwnerRepository
          </text>
        </g>
      </svg>
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 px-1 pt-1 text-[11px] text-slate-400">
        <span className="flex items-center gap-1.5"><OctagonAlert className="size-3.5" style={{ color: riskStyle.HIGH.hex }} /> High risk</span>
        <span className="flex items-center gap-1.5"><TriangleAlert className="size-3.5" style={{ color: riskStyle.MEDIUM.hex }} /> Medium risk</span>
        <span>Dashed: calls into the changed type</span>
      </div>
    </div>
  )
}

function Eyebrow({ children }: { children: string }) {
  return <p className="text-sm font-semibold text-indigo-600">{children}</p>
}

function Proof() {
  return (
    <section className="border-b border-slate-200 bg-white">
      <dl className="mx-auto grid max-w-6xl gap-8 px-6 py-14 sm:grid-cols-2 lg:grid-cols-4">
        {proof.map((p) => (
          <div key={p.label} className="border-l-2 border-indigo-500 pl-4">
            <dt className="text-3xl font-semibold tracking-tight text-slate-900 tabular-nums">{p.value}</dt>
            <dd className="mt-1 text-sm text-slate-600">{p.label}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

function Features() {
  return (
    <section id="features" className="bg-slate-50">
      <div className="mx-auto max-w-6xl px-6 py-24">
        <div className="max-w-2xl">
          <Eyebrow>Platform</Eyebrow>
          <h2 className="mt-2 text-3xl font-semibold tracking-tight text-slate-900 sm:text-4xl">Everything you need to change code safely</h2>
          <p className="mt-4 text-lg text-slate-600">
            Most bugs from a refactor are not in the lines you touched. They are in the code that quietly depends on them. CodeLens makes
            that code visible.
          </p>
        </div>
        <div className="mt-14 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {features.map((f) => (
            <div key={f.title} className="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md">
              <span className="inline-flex size-10 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600 ring-1 ring-indigo-100">
                <f.icon className="size-5" />
              </span>
              <h3 className="mt-5 text-base font-semibold text-slate-900">{f.title}</h3>
              <p className="mt-2 text-sm leading-6 text-slate-600">{f.body}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

function HowItWorks() {
  return (
    <section id="how" className="border-y border-slate-200 bg-white">
      <div className="mx-auto max-w-6xl px-6 py-24">
        <div className="mx-auto max-w-2xl text-center">
          <Eyebrow>How it works</Eyebrow>
          <h2 className="mt-2 text-3xl font-semibold tracking-tight text-slate-900 sm:text-4xl">From repository to risk map in minutes</h2>
        </div>
        <ol className="relative mt-16 grid gap-10 md:grid-cols-3">
          <div aria-hidden className="absolute top-6 right-[16%] left-[16%] hidden h-px bg-gradient-to-r from-indigo-200 via-violet-200 to-indigo-200 md:block" />
          {steps.map((s, i) => (
            <li key={s.title} className="relative text-center">
              <span className="relative mx-auto flex size-12 items-center justify-center rounded-full bg-white text-indigo-600 shadow-sm ring-1 ring-slate-200">
                <s.icon className="size-5" />
                <span className="absolute -top-1 -right-1 flex size-5 items-center justify-center rounded-full bg-indigo-600 text-[10px] font-semibold text-white">
                  {i + 1}
                </span>
              </span>
              <h3 className="mt-5 text-base font-semibold text-slate-900">{s.title}</h3>
              <p className="mx-auto mt-2 max-w-xs text-sm leading-6 text-slate-600">{s.body}</p>
            </li>
          ))}
        </ol>
      </div>
    </section>
  )
}

const Cite = ({ n }: { n: number }) => (
  <sup className="ml-0.5 inline-flex h-4 min-w-4 items-center justify-center rounded bg-violet-100 px-1 text-[10px] font-semibold text-violet-700">{n}</sup>
)

function LyraSection() {
  return (
    <section id="lyra" className="bg-gradient-to-b from-violet-50/70 to-slate-50">
      <div className="mx-auto grid max-w-6xl items-center gap-14 px-6 py-24 lg:grid-cols-2">
        <div>
          <div className="flex items-center gap-3">
            <LyraMark size={44} />
            <Eyebrow>Meet Lyra</Eyebrow>
          </div>
          <h2 className="mt-4 text-3xl font-semibold tracking-tight text-slate-900 sm:text-4xl">An assistant that has actually read your code</h2>
          <p className="mt-4 text-lg text-slate-600">
            Lyra is the AI built into CodeLens. Ask what a change will break, how a flow works or which tests to run, and get an answer
            built on the dependency graph rather than on a guess.
          </p>
          <dl className="mt-10 grid gap-6 sm:grid-cols-2">
            {lyraPoints.map((p) => (
              <div key={p.title}>
                <dt className="flex items-center gap-2 text-sm font-semibold text-slate-900">
                  <Check className="size-4 text-violet-600" /> {p.title}
                </dt>
                <dd className="mt-1.5 text-sm leading-6 text-slate-600">{p.body}</dd>
              </div>
            ))}
          </dl>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xl shadow-violet-900/5">
          <div className="flex justify-end">
            <p className="max-w-[85%] rounded-2xl rounded-br-sm bg-indigo-600 px-4 py-2.5 text-sm text-white">
              What breaks if I change OwnerRepository#findById?
            </p>
          </div>
          <div className="mt-5 flex gap-3">
            <LyraMark size={30} />
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold text-slate-900">Lyra</p>
              <div className="mt-1.5 space-y-3 rounded-2xl rounded-tl-sm border border-slate-200 bg-slate-50 p-4 text-sm leading-6 text-slate-700">
                <p>
                  <b className="font-semibold text-slate-900">OwnerController</b> calls it to load owners for the details and edit pages
                  <Cite n={1} />, and <b className="font-semibold text-slate-900">PetController</b> and{' '}
                  <b className="font-semibold text-slate-900">VisitController</b> resolve the owner through it before binding forms
                  <Cite n={2} />.
                </p>
                <div>
                  <p className="font-medium text-slate-900">Affected endpoints</p>
                  <ul className="mt-1 space-y-0.5 font-mono text-xs text-slate-600">
                    <li>GET /owners/{'{ownerId}'}</li>
                    <li>GET /owners/{'{ownerId}'}/edit</li>
                    <li>POST /owners/{'{ownerId}'}/pets/new</li>
                  </ul>
                </div>
                <p>
                  <span className="font-medium text-slate-900">Run first:</span> OwnerControllerTests, PetControllerTests
                  <Cite n={3} />
                </p>
              </div>
              <ol className="mt-3 flex flex-wrap gap-x-4 gap-y-1 font-mono text-[11px] text-slate-500">
                <li>[1] OwnerController.java</li>
                <li>[2] PetController.java</li>
                <li>[3] OwnerControllerTests.java</li>
              </ol>
              <div className="mt-3 flex flex-wrap gap-2 text-[11px]">
                <span className="rounded-md bg-slate-100 px-1.5 py-0.5 font-medium text-slate-700 ring-1 ring-slate-300 ring-inset">
                  Graph facts · static analysis
                </span>
                <span className="rounded-md bg-violet-50 px-1.5 py-0.5 font-medium text-violet-700 ring-1 ring-violet-200 ring-inset">
                  Explanation · Lyra
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

function Scoring() {
  const maxWeight = Math.max(...Object.values(factorWeight))
  const levels = [
    { level: 'LOW' as const, icon: ShieldCheck, range: 'below 30' },
    { level: 'MEDIUM' as const, icon: TriangleAlert, range: '30 to 59' },
    { level: 'HIGH' as const, icon: OctagonAlert, range: '60 and above' },
  ]
  return (
    <section id="scoring" className="border-t border-slate-200 bg-white">
      <div className="mx-auto grid max-w-6xl items-center gap-14 px-6 py-24 lg:grid-cols-2">
        <div>
          <Eyebrow>Risk scoring</Eyebrow>
          <h2 className="mt-2 text-3xl font-semibold tracking-tight text-slate-900 sm:text-4xl">No black-box scores</h2>
          <p className="mt-4 text-lg text-slate-600">
            A risk score you cannot explain is a risk score nobody trusts. CodeLens uses five measurable factors, each normalized against
            the rest of your codebase and weighted in the open.
          </p>
          <ul className="mt-8 space-y-3">
            {levels.map((l) => (
              <li key={l.level} className="flex items-center gap-3 text-sm text-slate-700">
                <l.icon className="size-5" style={{ color: riskStyle[l.level].hex }} />
                <span className="w-28 font-medium">{l.level.charAt(0) + l.level.slice(1).toLowerCase()} risk</span>
                <span className="text-slate-500">score {l.range}</span>
              </li>
            ))}
          </ul>
        </div>
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-6 shadow-sm">
          <p className="text-sm font-semibold text-slate-900">Score = Σ weight × normalized factor</p>
          <p className="mt-1 text-xs text-slate-500">Maximum contribution of each factor, out of 100 points</p>
          <ul className="mt-6 space-y-4">
            {Object.entries(factorWeight).map(([key, w]) => (
              <li key={key}>
                <div className="mb-1.5 flex items-baseline justify-between text-sm">
                  <span className="text-slate-700">{factorLabel[key]}</span>
                  <span className="font-semibold text-slate-900 tabular-nums">{Math.round(w * 100)} pts</span>
                </div>
                <div className="h-2 rounded bg-indigo-100">
                  <div className="h-full rounded bg-indigo-500" style={{ width: `${(w / maxWeight) * 100}%` }} />
                </div>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  )
}

function Stack() {
  return (
    <section className="border-t border-slate-200 bg-slate-50">
      <div className="mx-auto max-w-6xl px-6 py-14 text-center">
        <p className="text-sm font-medium text-slate-500">Built on proven open technology</p>
        <ul className="mt-5 flex flex-wrap justify-center gap-2.5">
          {stack.map((s) => (
            <li key={s} className="rounded-full border border-slate-200 bg-white px-3.5 py-1.5 text-sm text-slate-700 shadow-sm">
              {s}
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}

function FinalCta() {
  const { signedIn } = useAuth()
  return (
    <section className="relative overflow-hidden bg-slate-950">
      <div aria-hidden className="pointer-events-none absolute inset-0 bg-[radial-gradient(50%_80%_at_50%_100%,rgba(139,92,246,0.35),transparent)]" />
      <div className="relative mx-auto max-w-4xl px-6 py-24 text-center">
        <h2 className="text-3xl font-semibold tracking-tight text-balance text-white sm:text-4xl">Ship your next change with the blast radius in view.</h2>
        <p className="mx-auto mt-4 max-w-xl text-lg text-slate-300">Create an account, point CodeLens at a repository and see its riskiest code in minutes.</p>
        <div className="mt-9 flex flex-wrap justify-center gap-3">
          {signedIn ? (
            <Link to="/dashboard" className={primaryBtn}>
              Open your dashboard <ArrowRight className="size-4" />
            </Link>
          ) : (
            <>
              <Link to="/signup" className={primaryBtn}>
                Create free account <ArrowRight className="size-4" />
              </Link>
              <Link to="/signin" className={ghostBtn}>
                I already have an account
              </Link>
            </>
          )}
        </div>
      </div>
    </section>
  )
}

function Footer() {
  return (
    <footer className="border-t border-white/10 bg-slate-950 text-slate-400">
      <div className="mx-auto flex max-w-6xl flex-col gap-8 px-6 py-12 md:flex-row md:justify-between">
        <div className="max-w-xs">
          <div className="flex items-center gap-2">
            <Logo size={24} />
            <span className="font-semibold text-white">CodeLens</span>
          </div>
          <p className="mt-3 text-sm">Code intelligence and change-impact analysis for Java repositories.</p>
        </div>
        <div className="grid grid-cols-2 gap-10 text-sm">
          <div>
            <p className="font-medium text-white">Product</p>
            <ul className="mt-3 space-y-2">
              {navLinks.map((l) => (
                <li key={l.href}>
                  <a href={l.href} className="hover:text-white">{l.label}</a>
                </li>
              ))}
            </ul>
          </div>
          <div>
            <p className="font-medium text-white">Account</p>
            <ul className="mt-3 space-y-2">
              <li><Link to="/signup" className="hover:text-white">Create account</Link></li>
              <li><Link to="/signin" className="hover:text-white">Sign in</Link></li>
              <li><Link to="/dashboard" className="hover:text-white">Dashboard</Link></li>
            </ul>
          </div>
        </div>
      </div>
      <div className="border-t border-white/10">
        <p className="mx-auto max-w-6xl px-6 py-6 text-xs">© {new Date().getFullYear()} CodeLens. Built with Java, Python and React.</p>
      </div>
    </footer>
  )
}

export default function LandingPage() {
  return (
    <div className="bg-white">
      <main>
        <Hero />
        <Proof />
        <Features />
        <HowItWorks />
        <LyraSection />
        <Scoring />
        <Stack />
        <FinalCta />
      </main>
      <Footer />
    </div>
  )
}
