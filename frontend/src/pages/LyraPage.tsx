import { ChevronRight, Send } from 'lucide-react'
import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '@/api/client'
import { useAsk, useOverview } from '@/api/queries'
import { LyraMark } from '@/components/Lyra'
import { Markdown } from '@/components/Markdown'
import { useProjectContext } from '@/components/ProjectLayout'
import { Button, Card, ErrorState, Provenance } from '@/components/ui'
import { useProjectId } from '@/lib/hooks'
import type { AskResponse } from '@/types/api'

interface Turn {
  id: number
  question: string
  response?: AskResponse
  error?: unknown
}

function Answer({ turn, projectId }: { turn: Turn; projectId: number }) {
  const r = turn.response!
  const cite = (ref: number) =>
    document.getElementById(`src-${turn.id}-${ref}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  return (
    <div className="flex gap-3">
      <LyraMark size={30} />
      <Card className="min-w-0 flex-1 p-5">
        <div className="mb-3 flex items-center gap-2">
          <Provenance kind="ai" model={r.model} />
          {r.cached && <span className="text-[11px] text-slate-400">cached</span>}
        </div>
        <Markdown text={r.answer} onCite={cite} />

        {r.sources.length > 0 && (
          <div className="mt-4 border-t border-slate-100 pt-3">
            <p className="mb-1.5 text-[11px] font-semibold tracking-wide text-slate-400 uppercase">Sources</p>
            <ol className="space-y-1">
              {r.sources.map((s) => (
                <li key={s.ref} id={`src-${turn.id}-${s.ref}`} className="flex items-center gap-2 text-xs">
                  <span className="inline-flex h-4 min-w-4 items-center justify-center rounded bg-violet-100 px-1 text-[10px] font-semibold text-violet-700">
                    {s.ref}
                  </span>
                  {s.entityId ? (
                    <Link to={`/projects/${projectId}/entities/${s.entityId}`} className="font-mono text-indigo-600 hover:underline">
                      {s.label}
                    </Link>
                  ) : (
                    <span className="font-mono text-slate-700">{s.label}</span>
                  )}
                  <span className="truncate font-mono text-slate-400">
                    {s.path}:{s.startLine}-{s.endLine}
                  </span>
                </li>
              ))}
            </ol>
          </div>
        )}

        <details className="group mt-3 border-t border-slate-100 pt-3">
          <summary className="flex cursor-pointer list-none items-center gap-1.5 text-xs text-slate-500">
            <ChevronRight className="size-3.5 transition group-open:rotate-90" />
            Graph facts Lyra was given
            <Provenance kind="static" />
          </summary>
          <pre className="mt-2 max-h-80 overflow-auto rounded-lg bg-slate-50 p-3 font-mono text-[11px] text-slate-600">
            {JSON.stringify(r.facts, null, 2)}
          </pre>
        </details>
      </Card>
    </div>
  )
}

export default function LyraPage() {
  const projectId = useProjectId()
  const { project } = useProjectContext()
  const ask = useAsk(projectId)
  const { data: overview } = useOverview(projectId)
  const [turns, setTurns] = useState<Turn[]>([])
  const [question, setQuestion] = useState('')
  const nextId = useRef(1)
  const bottom = useRef<HTMLDivElement>(null)

  const top = overview?.topRisks[0]?.entity
  const examples = [
    top ? `What would be affected if I change ${top.label}?` : 'Which components are most risky to change?',
    'Which parts of the codebase have the highest coupling?',
    'What public APIs does this service expose and what do they depend on?',
    top ? `Explain how ${top.label.split('#')[0]} works and what it depends on.` : 'Explain the main modules.',
  ]

  const submit = async (q: string) => {
    const text = q.trim()
    if (!text || ask.isPending) return
    const id = nextId.current++
    setTurns((t) => [...t, { id, question: text }])
    setQuestion('')
    try {
      const response = await ask.mutateAsync(text)
      setTurns((t) => t.map((x) => (x.id === id ? { ...x, response } : x)))
    } catch (error) {
      setTurns((t) => t.map((x) => (x.id === id ? { ...x, error } : x)))
    }
    setTimeout(() => bottom.current?.scrollIntoView({ behavior: 'smooth' }), 50)
  }

  return (
    <div className="mx-auto flex h-full max-w-4xl flex-col px-6">
      <div className="flex-1 space-y-5 overflow-auto py-6">
        {turns.length === 0 && (
          <div className="py-10 text-center">
            <LyraMark size={52} className="mx-auto rounded-2xl" />
            <h2 className="mt-4 text-xl font-semibold tracking-tight text-slate-900">Ask Lyra about {project.name}</h2>
            <p className="mx-auto mt-2 max-w-lg text-sm text-slate-500">
              Lyra answers from the dependency graph and retrieved source. Graph facts come from static analysis; Lyra writes the
              explanation and cites the code it used.
            </p>
            <div className="mx-auto mt-7 grid max-w-2xl gap-2 sm:grid-cols-2">
              {examples.map((e) => (
                <button
                  key={e}
                  type="button"
                  onClick={() => submit(e)}
                  className="rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-left text-sm text-slate-700 shadow-sm transition hover:border-violet-300 hover:bg-violet-50"
                >
                  {e}
                </button>
              ))}
            </div>
          </div>
        )}
        {turns.map((turn) => (
          <div key={turn.id} className="space-y-3">
            <div className="ml-auto w-fit max-w-[80%] rounded-2xl rounded-br-sm bg-indigo-600 px-4 py-2.5 text-sm text-white">
              {turn.question}
            </div>
            {turn.response && <Answer turn={turn} projectId={projectId} />}
            {turn.error != null && (
              <ErrorState
                error={
                  turn.error instanceof ApiError && turn.error.status === 503
                    ? new Error(`Lyra is unavailable right now: ${turn.error.message}. Static analysis views still work.`)
                    : turn.error
                }
              />
            )}
            {!turn.response && turn.error == null && (
              <div className="flex items-center gap-3">
                <LyraMark size={30} className="animate-pulse" />
                <span className="text-sm text-slate-500">Lyra is reading graph facts and source…</span>
              </div>
            )}
          </div>
        ))}
        <div ref={bottom} />
      </div>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          submit(question)
        }}
        className="mb-6 flex items-end gap-2 rounded-xl border border-slate-200 bg-white p-2 shadow-sm focus-within:border-violet-300"
      >
        <textarea
          value={question}
          rows={2}
          onChange={(e) => setQuestion(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              submit(question)
            }
          }}
          placeholder="Ask Lyra: which classes depend on OrderService? What breaks if I change PaymentGateway#charge?"
          className="flex-1 resize-none bg-transparent px-2 py-1 text-sm outline-none placeholder:text-slate-400"
        />
        <Button type="submit" loading={ask.isPending} disabled={!question.trim()} aria-label="Ask Lyra">
          <Send className="size-4" />
        </Button>
      </form>
    </div>
  )
}
