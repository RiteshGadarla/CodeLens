import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

// renders llm output; [n] becomes a citation chip
export function Markdown({ text, onCite }: { text: string; onCite?: (ref: number) => void }) {
  const withCites = text.replace(/\[(\d{1,2})\](?!\()/g, '[$1](#cite-$1)')
  return (
    <div className="prose-sm max-w-none space-y-3 text-sm leading-relaxed text-slate-700 [&_code]:rounded [&_code]:bg-slate-100 [&_code]:px-1 [&_code]:py-0.5 [&_code]:font-mono [&_code]:text-[12px] [&_h1]:font-semibold [&_h2]:font-semibold [&_h3]:font-semibold [&_li]:ml-5 [&_ol]:list-decimal [&_pre]:overflow-auto [&_pre]:rounded-lg [&_pre]:bg-slate-950 [&_pre]:p-3 [&_pre_code]:bg-transparent [&_pre_code]:text-slate-200 [&_strong]:text-slate-900 [&_table]:w-full [&_td]:border [&_td]:border-slate-200 [&_td]:px-2 [&_th]:border [&_th]:border-slate-200 [&_th]:px-2 [&_ul]:list-disc">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          a: ({ href, children }) => {
            const m = href?.match(/^#cite-(\d+)$/)
            if (m) {
              return (
                <button
                  onClick={() => onCite?.(Number(m[1]))}
                  className="mx-0.5 inline-flex h-4 min-w-4 items-center justify-center rounded bg-violet-100 px-1 align-text-top text-[10px] font-semibold text-violet-700 hover:bg-violet-200"
                >
                  {children}
                </button>
              )
            }
            return (
              <a href={href} target="_blank" rel="noreferrer" className="text-indigo-600 underline">
                {children}
              </a>
            )
          },
        }}
      >
        {withCites}
      </ReactMarkdown>
    </div>
  )
}
