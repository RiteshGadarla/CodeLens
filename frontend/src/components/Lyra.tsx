import { Sparkles } from 'lucide-react'
import { cn } from '@/lib/cn'

export const LYRA = 'Lyra'

// avatar for the assistant
export function LyraMark({ size = 28, className }: { size?: number; className?: string }) {
  return (
    <span
      className={cn('inline-flex shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-violet-500 to-indigo-600 text-white shadow-sm', className)}
      style={{ width: size, height: size }}
      aria-hidden
    >
      <Sparkles style={{ width: size * 0.55, height: size * 0.55 }} />
    </span>
  )
}
