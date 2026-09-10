import { cn } from '@/lib/cn'

export function CodeBlock({ code, startLine = 1, className }: { code: string; startLine?: number; className?: string }) {
  const lines = code.split('\n')
  return (
    <div className={cn('overflow-auto rounded-lg bg-slate-950 font-mono text-[12.5px] leading-5', className)}>
      <table className="w-full border-collapse">
        <tbody>
          {lines.map((line, i) => (
            <tr key={i} className="hover:bg-white/5">
              <td className="w-12 select-none pr-3 pl-3 text-right align-top text-slate-600">{startLine + i}</td>
              <td className="pr-4 whitespace-pre text-slate-200">{line || ' '}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
