import { Bot, ChevronRight, Send, Sparkles } from 'lucide-react'
import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '@/api/client'
import { useAsk, useOverview } from '@/api/queries'
import { Markdown } from '@/components/Markdown'
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
    <Card className="p-5">
      <div className="mb-3 flex items-center gap-2">
        <Sparkles className="size-4 text-violet-500" />
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
          Graph facts given to the model
          <Provenance kind="static" />
        </summary>
        <pre className="mt-2 max-h-80 overflow-auto rounded-lg bg-slate-50 p-3 font-mono text-[11px] text-slate-600">
          {JSON.stringify(r.facts, null, 2)}
        </pre>
      </details>
    </Card>
  )
}

export default function AssistantPage() {
  const projectId = useProjectId()
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
            <Bot className="mx-auto size-10 text-violet-400" />
            <h2 className="mt-3 text-lg font-semibold text-slate-900">Ask about this codebase</h2>
            <p className="mx-auto mt-1 max-w-lg text-sm text-slate-500">
              Answers are grounded in the dependency graph and retrieved source. Graph facts come from static analysis;
              the explanation is written by the model.
            </p>
            <div className="mx-auto mt-6 grid max-w-2xl gap-2 sm:grid-cols-2">
              {examples.map((e) => (
                <button
                  key={e}
                  onClick={() => submit(e)}
                  className="rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-left text-sm text-slate-700 shadow-sm hover:border-violet-300 hover:bg-violet-50"
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
                    ? new Error(`AI service unavailable: ${turn.error.message}. Static analysis views still work.`)
                    : turn.error
                }
              />
            )}
            {!turn.response && turn.error == null && (
              <Card className="flex items-center gap-2 p-4 text-sm text-slate-500">
                <Sparkles className="size-4 animate-pulse text-violet-500" /> Retrieving graph facts and source…
              </Card>
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
          placeholder="Which classes depend on OrderService? What breaks if I change PaymentGateway#charge?"
          className="flex-1 resize-none bg-transparent px-2 py-1 text-sm outline-none placeholder:text-slate-400"
        />
        <Button type="submit" loading={ask.isPending} disabled={!question.trim()}>
          <Send className="size-4" />
        </Button>
      </form>
    </div>
  )
}
